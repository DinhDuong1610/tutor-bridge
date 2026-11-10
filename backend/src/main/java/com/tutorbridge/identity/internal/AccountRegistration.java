package com.tutorbridge.identity.internal;

import com.tutorbridge.identity.internal.AuthRateLimiter.Action;
import com.tutorbridge.identity.internal.AuthRateLimiter.Check;
import com.tutorbridge.identity.internal.AuthRateLimiter.Scope;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * UC01 steps 1 and 2: registerAccount, verifyEmail, resendVerification. All three are public and
 * take no Idempotency-Key (CR-014).
 *
 * <p>Order of work in each command: count the attempt (own transaction), do slow work such as
 * hashing outside any transaction, then write account, challenge and outbox event in one commit
 * (INV-18, INV-21). The email itself is sent later from the outbox.
 */
@Service
public class AccountRegistration {

    /** Event read by the mail sender. The payload holds the code only encrypted for this challenge. */
    public static final String VERIFICATION_REQUESTED = "identity.EmailVerificationRequested";

    static final Duration VERIFY_LINK_LIFETIME = Duration.ofMinutes(30);
    private static final Set<String> SELF_SERVICE_ROLES = Set.of("PARENT", "TUTOR");

    public record Registration(String email, String password, String displayName, String role, String termsVersion) {}

    private final AccountRepository accounts;
    private final AccountSecrets secrets;
    private final PasswordHasher hasher;
    private final AuthRateLimiter limiter;
    private final LinkIssuer links;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;
    private final IdentityProperties properties;

    AccountRegistration(AccountRepository accounts, AccountSecrets secrets, PasswordHasher hasher,
            AuthRateLimiter limiter, LinkIssuer links, TransactionOperations transactions, Clock clock,
            IdGenerator ids, IdentityProperties properties) {
        this.accounts = accounts;
        this.secrets = secrets;
        this.hasher = hasher;
        this.limiter = limiter;
        this.links = links;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
        this.properties = properties;
    }

    /**
     * Creates an UNVERIFIED account and queues the verification email. A taken email writes nothing
     * and returns exactly as a new one does (AC-UC01-01), after the same hashing work.
     */
    public void register(Registration input, String source) {
        validate(input);
        IdentityProperties.Limits limits = properties.limits();
        limiter.hit(List.of(new Check(Action.REGISTER, Scope.SOURCE, secrets.sourceDigest(source),
                limits.registerPerSource())));

        String email = AccountSecrets.normalizeEmail(input.email());
        String passwordHash = hasher.hash(input.password());
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            UUID accountId = ids.generateId();
            boolean created = accounts.insertUnverified(accountId, input.role(), secrets.emailDigest(email),
                    secrets.encryptEmail(accountId, email), input.displayName().strip(), now);
            if (!created) {
                return;
            }
            accounts.insertCredential(accountId, passwordHash, PasswordHasher.POLICY, now);
            accounts.insertTermsAcceptance(accountId, input.termsVersion(), now);
            queueVerification(accountId, now);
        });
    }

    /** Consumes a VERIFY_EMAIL code once and activates its account (AC-UC01-02). */
    public void verifyEmail(String token) {
        String digest = AccountSecrets.linkTokenDigest(token);
        transactions.executeWithoutResult(status -> {
            Instant now = clock.instant();
            UUID accountId = accounts.consumeChallenge(digest, AccountRepository.VERIFY_EMAIL, now)
                    .orElseThrow(() -> LinkCodes.rejection(accounts, digest, AccountRepository.VERIFY_EMAIL));
            if (!accounts.activate(accountId, now)
                    && !"ACTIVE".equals(accounts.status(accountId).orElse(null))) {
                // LOCKED or DELETED: never reactivate; the rollback leaves the code unconsumed.
                throw LinkCodes.invalid();
            }
        });
    }

    /**
     * Queues a new code for an UNVERIFIED account. Every caller gets the same answer, whether the
     * account exists or not, so the counter is keyed by the email digest, not the account.
     */
    public void resendVerification(String rawEmail, String source) {
        String email = AccountSecrets.normalizeEmail(rawEmail);
        String emailDigest = secrets.emailDigest(email);
        IdentityProperties.Limits limits = properties.limits();
        limiter.hit(List.of(
                new Check(Action.RESEND_VERIFICATION, Scope.ACCOUNT, emailDigest, limits.resendPerAccount()),
                new Check(Action.RESEND_VERIFICATION, Scope.SOURCE, secrets.sourceDigest(source),
                        limits.resendPerSource())));
        transactions.executeWithoutResult(status -> accounts.lockUnverifiedByEmail(emailDigest)
                .ifPresent(accountId -> queueVerification(accountId, clock.instant())));
    }

    private void queueVerification(UUID accountId, Instant now) {
        links.issue(accountId, AccountRepository.VERIFY_EMAIL, VERIFY_LINK_LIFETIME, VERIFICATION_REQUESTED, now);
    }

    /** Rules the contract states but bean validation cannot: code points, allowlists, blank names. */
    private void validate(Registration input) {
        List<FieldProblem> problems = new ArrayList<>();
        if (!PasswordHasher.acceptableLength(input.password())) {
            problems.add(new FieldProblem("password", "Mật khẩu cần từ 15 đến 128 ký tự."));
        }
        if (!SELF_SERVICE_ROLES.contains(input.role())) {
            problems.add(new FieldProblem("role", "Chỉ phụ huynh hoặc gia sư được tự đăng ký."));
        }
        if (input.displayName().isBlank()) {
            problems.add(new FieldProblem("displayName", "Vui lòng nhập tên hiển thị."));
        }
        if (!properties.termsVersions().contains(input.termsVersion())) {
            problems.add(new FieldProblem("termsVersion", "Phiên bản điều khoản không còn áp dụng."));
        }
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    problems);
        }
    }
}
