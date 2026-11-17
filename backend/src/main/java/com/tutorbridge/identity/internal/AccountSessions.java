package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.Cursor;
import com.tutorbridge.platform.api.Page;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * UC01 steps 3–5: loginAccount, logoutAccount, listOwnSessions, revokeOwnSession.
 *
 * <p>Login: refuse while delayed, compare the password outside any transaction (ADR-0008), record a
 * failure in its own statement, then issue an opaque session in one commit. Every failure that could
 * tell whether an email has an account, or is an admin, gives the same 401 (Q13, Q14, Q17 of TB-8).
 */
@Service
public class AccountSessions {

    public record Credential(UUID sessionId, String accessToken, String role, Instant idleExpiresAt,
            Instant absoluteExpiresAt) {

        @Override
        public String toString() {
            return "Credential[sessionId=%s, accessToken=%s, role=%s, idleExpiresAt=%s, absoluteExpiresAt=%s]"
                    .formatted(sessionId, Masked.VALUE, role, idleExpiresAt, absoluteExpiresAt);
        }
    }

    public record OwnSession(UUID id, String deviceLabel, Instant lastSeenAt, Instant expiresAt, boolean current,
            boolean revoked) {}

    private final SessionRepository sessions;
    private final AccountSecrets secrets;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    AccountSessions(SessionRepository sessions, AccountSecrets secrets, PasswordHasher hasher,
            LoginThrottle throttle, TransactionOperations transactions, Clock clock, IdGenerator ids) {
        this.sessions = sessions;
        this.secrets = secrets;
        this.hasher = hasher;
        this.throttle = throttle;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    public Credential login(String rawEmail, String password, String source, String userAgent) {
        String emailDigest = secrets.emailDigest(AccountSecrets.normalizeEmail(rawEmail));
        String sourceDigest = secrets.sourceDigest(source);
        // Counted as a failure before the password check, so concurrent tries cannot pass the limit (#141).
        LoginThrottle.Attempt attempt = throttle.begin(emailDigest, sourceDigest);

        Optional<SessionRepository.Login> found = sessions.findLogin(emailDigest);
        boolean passwordMatches = hasher.matches(password, found.map(SessionRepository.Login::passwordHash).orElse(null));
        SessionRepository.Login login = found.orElse(null);
        if (!passwordMatches || "ADMIN".equals(login.role()) || "DELETED".equals(login.status())) {
            throttle.failed(attempt);
            throw authenticationFailed();
        }
        throttle.passed(attempt);
        // Only after a correct password, so these never reveal that an email has an account (Q5).
        switch (login.status()) {
            case "UNVERIFIED" -> throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNVERIFIED",
                    "Email chưa được xác minh. Mở liên kết trong email, hoặc gửi lại liên kết.");
            case "LOCKED" -> throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED",
                    "Tài khoản đang bị tạm khóa nên chưa đăng nhập được.");
            default -> { }
        }

        String newHash = PasswordHasher.POLICY.equals(login.hashPolicy()) ? null : hasher.hash(password);
        String token = secrets.newLinkToken();
        UUID sessionId = ids.generateId();
        Instant now = clock.instant();
        Boolean issued = transactions.execute(status -> {
            if (!sessions.insertSession(sessionId, login.accountId(), login.authEpoch(),
                    AccountSecrets.linkTokenDigest(token), deviceLabel(userAgent), now)) {
                status.setRollbackOnly();
                return false;
            }
            if (newHash != null) {
                sessions.rehash(login.accountId(), newHash, PasswordHasher.POLICY, now);
            }
            sessions.clearAccountFailures(emailDigest);
            return true;
        });
        if (!Boolean.TRUE.equals(issued)) {
            // The account was locked or its epoch moved between the check and the insert.
            throw authenticationFailed();
        }
        return new Credential(sessionId, token, login.role(), now.plus(SessionRepository.IDLE),
                now.plus(SessionRepository.ABSOLUTE));
    }

    /** Idempotent: the session behind this request is revoked, or already was. */
    public void logout(Actor actor) {
        sessions.revoke(actor.sessionId(), actor.accountId(), clock.instant());
    }

    public Page<OwnSession> listOwn(Actor actor, String cursor, Integer requestedLimit) {
        int limit = Page.limit(requestedLimit);
        Cursor after = Cursor.decode(cursor);
        List<SessionRepository.Listed> rows = sessions.listOwn(actor.accountId(), clock.instant(), after, limit + 1);
        Page<SessionRepository.Listed> page = Page.of(rows, limit, row -> new Cursor(row.createdAt(), row.id()));
        return new Page<>(page.items().stream()
                .map(row -> new OwnSession(row.id(), row.deviceLabel(), row.lastSeenAt(), row.expiresAt(),
                        row.id().equals(actor.sessionId()), row.revoked()))
                .toList(), page.nextCursor());
    }

    /** Another account's session and an unknown id get the same 404 (INV-06). */
    public void revokeOwn(Actor actor, UUID sessionId) {
        if (!sessions.revoke(sessionId, actor.accountId(), clock.instant())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Không tìm thấy phiên đăng nhập.");
        }
    }

    private static ApiException authenticationFailed() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Email hoặc mật khẩu không đúng.");
    }

    /**
     * A coarse label from the User-Agent, never the raw header. Unknown clients get an empty label,
     * which the app shows as "Thiết bị không rõ" (#90). React Native on Android sends okhttp.
     */
    static String deviceLabel(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("android") || ua.startsWith("okhttp")) {
            return "Android";
        }
        if (ua.contains("iphone")) {
            return "iPhone";
        }
        if (ua.contains("ipad")) {
            return "iPad";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os")) {
            return "macOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return "";
    }
}
