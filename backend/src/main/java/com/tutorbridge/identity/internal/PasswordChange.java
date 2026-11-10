package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * UC01 step 4 for a signed-in account: changePassword and reauthenticate. Both check the current
 * password outside any transaction (ADR-0008). A wrong password is 422 CURRENT_PASSWORD_INVALID,
 * never 401, so the app keeps its session (Q10 of TB-10), and counts toward the login delay (Q3 of
 * TB-9). Neither takes an Idempotency-Key (CR-014).
 */
@Service
public class PasswordChange {

    static final Duration REAUTH_LIFETIME = Duration.ofMinutes(5);
    static final String REAUTH = "REAUTH";

    public record ReauthToken(String reauthToken, Instant expiresAt) {}

    private final SessionRepository sessions;
    private final AccountRepository accounts;
    private final AccountSecrets secrets;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    PasswordChange(SessionRepository sessions, AccountRepository accounts, AccountSecrets secrets,
            PasswordHasher hasher, LoginThrottle throttle, TransactionOperations transactions, Clock clock,
            IdGenerator ids) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.secrets = secrets;
        this.hasher = hasher;
        this.throttle = throttle;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    /**
     * Stores the new hash, raises auth_epoch and revokes every session of the account, the current one
     * included, and clears its login failures by email (UC01, AC-UC01-03, Q4 of TB-9).
     */
    public void changePassword(Actor actor, String currentPassword, String newPassword, String source) {
        if (!PasswordHasher.acceptableLength(newPassword)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    List.of(new FieldProblem("newPassword", "Mật khẩu cần từ 15 đến 128 ký tự.")));
        }
        SessionRepository.Own own = checkPassword(actor, currentPassword, "currentPassword", source);
        String newHash = hasher.hash(newPassword);
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            AccountRepository.Locked locked = accounts.lockAccount(actor.accountId()).orElseThrow(PasswordChange::sessionEnded);
            if (!"ACTIVE".equals(locked.status()) || locked.authEpoch() != own.authEpoch()) {
                // Locked, or another reset or change won in between: this session is over anyway.
                throw sessionEnded();
            }
            accounts.replacePassword(actor.accountId(), newHash, PasswordHasher.POLICY, now);
            sessions.clearAccountFailures(own.emailDigest());
        });
    }

    /**
     * A one-time token for a sensitive step that needs a fresh password (Q1 of TB-9): stored as a
     * REAUTH challenge digest, valid five minutes. {@code purpose} is checked against the contract's
     * enum by the controller and not stored; the consumer comes with F06.
     */
    public ReauthToken reauthenticate(Actor actor, String password, String source) {
        checkPassword(actor, password, "password", source);
        String token = secrets.newLinkToken();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(REAUTH_LIFETIME);
        transactions.executeWithoutResult(status -> accounts.insertChallenge(ids.generateId(), actor.accountId(),
                REAUTH, AccountSecrets.linkTokenDigest(token), now, expiresAt));
        return new ReauthToken(token, expiresAt);
    }

    private SessionRepository.Own checkPassword(Actor actor, String password, String field, String source) {
        SessionRepository.Own own = sessions.findOwn(actor.accountId()).orElseThrow(PasswordChange::sessionEnded);
        String sourceDigest = secrets.sourceDigest(source);
        throttle.refuseWhileDelayed(own.emailDigest(), sourceDigest);
        if (!hasher.matches(password, own.passwordHash())) {
            throttle.recordFailure(own.emailDigest(), sourceDigest);
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CURRENT_PASSWORD_INVALID",
                    "Mật khẩu hiện tại không đúng.",
                    List.of(new FieldProblem(field, "Mật khẩu hiện tại không đúng.")));
        }
        return own;
    }

    private static ApiException sessionEnded() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    }
}
