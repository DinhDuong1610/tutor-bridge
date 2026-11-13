package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.TooManyRequests;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Five wrong passwords in the last 15 minutes, by email or by source, delay the next try until the
 * oldest of those five is 15 minutes old (UC01; sliding window, Đính 10/11/2026). Login, change of
 * password and reauthentication share it (Q3 of TB-9). A refused try is not a failure: no password
 * was checked. State lives in auth_login_failures (INV-80).
 *
 * <p>Every try is counted <em>before</em> its password is checked (Issue #141). {@link #begin} locks
 * both subjects, refuses a delayed try and records this one as a failure in one short transaction,
 * so concurrent tries cannot all see fewer than five failures. The slow password check runs after
 * that transaction, holding no lock (ADR-0008). A correct password then takes the try back with
 * {@link #passed}; a wrong one leaves it counted.
 */
@Component
class LoginThrottle {

    static final int FAILURES_BEFORE_DELAY = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    /** A try that is already counted as a failure until {@link #passed} takes it back. */
    record Attempt(List<Long> failureIds) {}

    private final SessionRepository sessions;
    private final TransactionOperations transactions;
    private final Clock clock;

    LoginThrottle(SessionRepository sessions, TransactionOperations transactions, Clock clock) {
        this.sessions = sessions;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Refuses with 429 LOGIN_DELAYED while either subject is delayed; otherwise records this try as a
     * failure of both subjects. Call before checking the password.
     */
    Attempt begin(String emailDigest, String sourceDigest) {
        return transactions.execute(status -> {
            // Fixed lock order: "ACCOUNT" sorts before "SOURCE" (INV-16, ADR-0007).
            sessions.lockSubject("ACCOUNT", emailDigest);
            sessions.lockSubject("SOURCE", sourceDigest);
            Instant now = clock.instant();
            refuseWhileDelayed(emailDigest, sourceDigest, now);
            return new Attempt(List.of(
                    sessions.recordFailure("ACCOUNT", emailDigest, now),
                    sessions.recordFailure("SOURCE", sourceDigest, now)));
        });
    }

    /** The password was correct: this try is not a failure. */
    void passed(Attempt attempt) {
        sessions.deleteFailures(attempt.failureIds());
    }

    private void refuseWhileDelayed(String emailDigest, String sourceDigest, Instant now) {
        Duration wait = Duration.ZERO;
        for (var subject : List.of(List.of("ACCOUNT", emailDigest), List.of("SOURCE", sourceDigest))) {
            List<Instant> recent = sessions.recentFailures(subject.get(0), subject.get(1), now.minus(WINDOW),
                    FAILURES_BEFORE_DELAY);
            if (recent.size() >= FAILURES_BEFORE_DELAY) {
                Duration w = Duration.between(now, recent.getLast().plus(WINDOW));
                if (w.compareTo(wait) > 0) {
                    wait = w;
                }
            }
        }
        if (!wait.isZero() && !wait.isNegative()) {
            throw new TooManyRequests("LOGIN_DELAYED", "Bạn đã nhập sai nhiều lần. Vui lòng thử lại sau.", wait);
        }
    }
}
