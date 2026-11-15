package com.tutorbridge.identity.internal;

import com.tutorbridge.identity.internal.AuthRateLimiter.Action;
import com.tutorbridge.identity.internal.AuthRateLimiter.Check;
import com.tutorbridge.identity.internal.AuthRateLimiter.Scope;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * UC01 step 4, recovery by email: requestRecovery and resetPassword. Both are public and take no
 * Idempotency-Key (CR-014).
 */
@Service
public class PasswordRecovery {

    /** Event read by {@link PasswordResetMailSender}. */
    public static final String RESET_REQUESTED = "identity.PasswordResetRequested";

    static final Duration RESET_LINK_LIFETIME = Duration.ofMinutes(15);

    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final AccountSecrets secrets;
    private final PasswordHasher hasher;
    private final AuthRateLimiter limiter;
    private final LinkIssuer links;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdentityProperties properties;

    PasswordRecovery(AccountRepository accounts, SessionRepository sessions, AccountSecrets secrets,
            PasswordHasher hasher, AuthRateLimiter limiter, LinkIssuer links, TransactionOperations transactions,
            Clock clock, IdentityProperties properties) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.secrets = secrets;
        this.hasher = hasher;
        this.limiter = limiter;
        this.links = links;
        this.transactions = transactions;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Queues a reset link for an ACTIVE or UNVERIFIED account (Q2 of TB-9). Every caller gets the
     * same answer, so the counter is keyed by the email digest, whether an account exists or not.
     */
    public void requestRecovery(String rawEmail, String source) {
        String emailDigest = secrets.emailDigest(AccountSecrets.normalizeEmail(rawEmail));
        IdentityProperties.Limits limits = properties.limits();
        limiter.hit(List.of(
                new Check(Action.RECOVERY, Scope.ACCOUNT, emailDigest, limits.recoveryPerAccount()),
                new Check(Action.RECOVERY, Scope.SOURCE, secrets.sourceDigest(source), limits.recoveryPerSource())));
        transactions.executeWithoutResult(status -> accounts.lockRecoverableByEmail(emailDigest)
                .ifPresent(accountId -> links.issue(accountId, AccountRepository.RESET_PASSWORD,
                        RESET_LINK_LIFETIME, RESET_REQUESTED, clock.instant())));
    }

    /**
     * Consumes a RESET_PASSWORD code once and, in the same commit, stores the new hash, raises
     * auth_epoch, revokes every session, spends the account's other reset links and clears its login
     * failures by email (UC01, AC-UC01-02, AC-UC01-03, Q4 of TB-9). The status does not change.
     */
    public void resetPassword(String token, String newPassword, String source) {
        if (!PasswordHasher.acceptableLength(newPassword)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    List.of(new FieldProblem("newPassword", "Mật khẩu cần từ 15 đến 128 ký tự.")));
        }
        // A reset is the second step of recovery, so it counts against the recovery limit per source
        // (Huy, 13/11/2026, Issue #140). There is no email to key a per-account counter on.
        limiter.hit(List.of(new Check(Action.RECOVERY, Scope.SOURCE, secrets.sourceDigest(source),
                properties.limits().recoveryPerSource())));
        String digest = AccountSecrets.linkTokenDigest(token);
        // Argon2id costs about 19 MiB and real CPU (ADR-0008): only hash for a token that can still be
        // used (Issue #140). The consume below checks it again atomically, so a token used meanwhile
        // is still refused.
        transactions.executeWithoutResult(status -> {
            if (accounts.findChallenge(digest)
                    .filter(c -> AccountRepository.RESET_PASSWORD.equals(c.purpose()))
                    .filter(c -> c.consumedAt() == null && c.expiresAt().isAfter(clock.instant()))
                    .isEmpty()) {
                throw LinkCodes.rejection(accounts, digest, AccountRepository.RESET_PASSWORD);
            }
        });
        String newHash = hasher.hash(newPassword);
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            UUID accountId = accounts.consumeChallenge(digest, AccountRepository.RESET_PASSWORD, now)
                    .orElseThrow(() -> LinkCodes.rejection(accounts, digest, AccountRepository.RESET_PASSWORD));
            AccountRepository.Locked account = accounts.lockAccount(accountId).orElseThrow(LinkCodes::invalid);
            if (!"ACTIVE".equals(account.status()) && !"UNVERIFIED".equals(account.status())) {
                // LOCKED or DELETED since the link was sent: the rollback leaves the code unconsumed.
                throw LinkCodes.invalid();
            }
            accounts.replacePassword(accountId, newHash, PasswordHasher.POLICY, now);
            accounts.consumeOpenChallenges(accountId, AccountRepository.RESET_PASSWORD, now);
            sessions.clearAccountFailures(account.emailDigest());
        });
    }
}
