package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * The admin sign-in (TB-13; G3-D16-02): loginAdmin, completeAdminMfa and getAdminSession.
 *
 * <p>loginAdmin follows loginAccount (Q17 on #136): the try is counted before the slow password check,
 * and an unknown email, a wrong password, a non-admin and an admin without a live MFA enrollment all get
 * the same 401. A correct password gives a single-use challenge, never a session, and does not clear the
 * email's failures: only a right code does.
 *
 * <p>completeAdminMfa takes a TOTP code (Đính 23/11/2026 on #13). Every try is also counted against the
 * email with the same five-in-fifteen-minutes delay, so a known password cannot be used to try every code.
 * A wrong code counts on the challenge too, and five end it. The challenge row is locked, so two tries of one
 * challenge run in turn; the accepted step is recorded, so one code never opens two sessions.
 */
@Service
public class AdminSignIn {

    /** How long the second step may wait; V5 allows at most 15 minutes. Agent's choice, see #13. */
    static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(5);
    /** Wrong codes that end one challenge (Đính, #13). */
    static final int WRONG_CODES_PER_CHALLENGE = 5;

    /** {@code AdminMfaChallenge}. The token is a secret: never logged, never stored in clear. */
    public record Challenge(boolean mfaRequired, String challengeToken, Instant expiresAt) {

        @Override
        public String toString() {
            return "Challenge[mfaRequired=%s, challengeToken=%s, expiresAt=%s]".formatted(mfaRequired,
                    Masked.VALUE, expiresAt);
        }
    }

    /** {@code AdminSessionView}. */
    public record AdminSession(UUID sessionId, String role, Instant mfaVerifiedAt, Instant idleExpiresAt,
            Instant absoluteExpiresAt) {}

    private enum Outcome { SIGNED_IN, WRONG_CODE, ENDED }

    private final SessionRepository sessions;
    private final AdminMfaRepository mfa;
    private final AccountSecrets secrets;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    AdminSignIn(SessionRepository sessions, AdminMfaRepository mfa, AccountSecrets secrets, PasswordHasher hasher,
            LoginThrottle throttle, TransactionOperations transactions, Clock clock, IdGenerator ids) {
        this.sessions = sessions;
        this.mfa = mfa;
        this.secrets = secrets;
        this.hasher = hasher;
        this.throttle = throttle;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    public Challenge login(String rawEmail, String password, String source) {
        String emailDigest = secrets.emailDigest(AccountSecrets.normalizeEmail(rawEmail));
        LoginThrottle.Attempt attempt = throttle.begin(emailDigest, secrets.sourceDigest(source));

        Optional<SessionRepository.Login> found = sessions.findLogin(emailDigest);
        boolean passwordMatches = hasher.matches(password, found.map(SessionRepository.Login::passwordHash).orElse(null));
        SessionRepository.Login login = found.orElse(null);
        // Q17 points 1 and 4: none of these may tell whether the email is an admin or has MFA. A DELETED
        // account counts as a failure too, as in loginAccount (Huy's review of #217).
        if (!passwordMatches || !"ADMIN".equals(login.role()) || !mfa.hasLiveEnrollment(login.accountId())
                || !("ACTIVE".equals(login.status()) || "LOCKED".equals(login.status()))) {
            throttle.failed(attempt);
            throw signInFailed();
        }
        throttle.passed(attempt);
        // Only after a correct password (Q17 point 5).
        if ("LOCKED".equals(login.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED",
                    "Tài khoản đang bị tạm khóa nên chưa đăng nhập được.");
        }

        String newHash = PasswordHasher.POLICY.equals(login.hashPolicy()) ? null : hasher.hash(password);
        String token = secrets.newLinkToken();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(CHALLENGE_LIFETIME);
        transactions.executeWithoutResult(status -> {
            mfa.insertChallenge(ids.generateId(), login.accountId(), AccountSecrets.linkTokenDigest(token), now,
                    expiresAt);
            if (newHash != null) {
                sessions.rehash(login.accountId(), newHash, PasswordHasher.POLICY, now);
            }
        });
        return new Challenge(true, token, expiresAt);
    }

    public AccountSessions.Credential completeMfa(String challengeToken, String code, String source, String userAgent) {
        String digest = AccountSecrets.linkTokenDigest(challengeToken);
        AdminMfaRepository.ChallengeOwner owner = mfa.findOwner(digest).orElseThrow(AdminSignIn::challengeEnded);
        LoginThrottle.Attempt attempt = throttle.begin(owner.emailDigest(), secrets.sourceDigest(source));

        String token = secrets.newLinkToken();
        UUID sessionId = ids.generateId();
        Instant now = clock.instant();
        Outcome outcome = transactions.execute(status -> {
            AdminMfaRepository.Locked challenge = mfa.lock(digest).orElse(null);
            if (challenge == null || challenge.consumedAt() != null || !now.isBefore(challenge.expiresAt())
                    || challenge.failedAttempts() >= WRONG_CODES_PER_CHALLENGE || challenge.secretHandle() == null
                    || !"ADMIN".equals(challenge.role()) || !"ACTIVE".equals(challenge.accountStatus())) {
                return Outcome.ENDED;
            }
            byte[] secret = secrets.decryptTotpSecret(challenge.accountId(), challenge.secretHandle());
            OptionalLong step = Totp.verify(secret, code, now, challenge.lastUsedStep());
            if (step.isEmpty() || !mfa.useStep(challenge.accountId(), step.getAsLong())) {
                mfa.countWrongCode(challenge.id());
                return Outcome.WRONG_CODE;
            }
            mfa.consume(challenge.id(), now);
            if (!sessions.insertAdminSession(sessionId, challenge.accountId(), challenge.authEpoch(),
                    AccountSecrets.linkTokenDigest(token), AccountSessions.deviceLabel(userAgent), now)) {
                // The account was locked or its epoch moved after the lock above: nothing of this try stays.
                status.setRollbackOnly();
                return Outcome.ENDED;
            }
            sessions.clearAccountFailures(owner.emailDigest());
            return Outcome.SIGNED_IN;
        });
        switch (outcome) {
            case SIGNED_IN -> throttle.passed(attempt);
            case WRONG_CODE -> {
                throttle.failed(attempt);
                throw wrongCode();
            }
            default -> {
                throttle.failed(attempt);
                throw challengeEnded();
            }
        }
        return new AccountSessions.Credential(sessionId, token, "ADMIN", now.plus(SessionRepository.ADMIN_IDLE),
                now.plus(SessionRepository.ADMIN_ABSOLUTE));
    }

    /**
     * getAdminSession. The resolver accepts an ADMIN session only after its second factor (V5
     * {@code valid_session}), so here a session of another role is the only one refused, with 403.
     */
    public AdminSession session(Actor actor) {
        if (actor.role() != Actor.Role.ADMIN) {
            throw accessDenied();
        }
        SessionRepository.AdminSession session = sessions.findAdminSession(actor.sessionId())
                .orElseThrow(AdminSignIn::accessDenied);
        return new AdminSession(actor.sessionId(), "ADMIN", session.mfaVerifiedAt(),
                session.lastSeenAt().plus(SessionRepository.ADMIN_IDLE),
                session.createdAt().plus(SessionRepository.ADMIN_ABSOLUTE));
    }

    /** AD01, approved wording. */
    private static ApiException signInFailed() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Email hoặc mật khẩu chưa đúng.");
    }

    /** AD01, approved wording; the screen adds its "Lỗi: " label. */
    private static ApiException wrongCode() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CODE_INVALID",
                "Mã chưa đúng hoặc đã hết hạn. Nhập mã mới từ ứng dụng xác thực.");
    }

    /**
     * An unknown, used, expired or ended challenge. Same AD01 words as a wrong code (AD01 has one error for
     * step 2); the code tells the admin web to go back to the password step.
     */
    private static ApiException challengeEnded() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CHALLENGE_EXPIRED",
                "Mã chưa đúng hoặc đã hết hạn. Nhập mã mới từ ứng dụng xác thực.");
    }

    private static ApiException accessDenied() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Tài khoản không thể thực hiện thao tác này.");
    }
}
