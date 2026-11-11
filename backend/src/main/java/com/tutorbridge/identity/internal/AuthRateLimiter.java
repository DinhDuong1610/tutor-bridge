package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.TooManyRequests;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fixed-window attempt counters on {@code auth_rate_counters} for register, resend and recovery
 * (Đính, 10/11/2026). State lives in the database, so every instance sees the same count (INV-80).
 *
 * <p>Each check commits in its own short transaction, before and apart from the command, so an
 * attempt is counted even when the command later fails.
 */
@Component
class AuthRateLimiter {

    enum Action { REGISTER, RESEND_VERIFICATION, RECOVERY }

    enum Scope { ACCOUNT, SOURCE }

    record Check(Action action, Scope scope, String subjectDigest, IdentityProperties.Limit limit) {}

    private final JdbcClient jdbc;
    private final Clock clock;
    private final TransactionTemplate ownTransaction;

    AuthRateLimiter(JdbcClient jdbc, Clock clock, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Counts one attempt against every check, then rejects if any is over its limit. */
    void hit(List<Check> checks) {
        Instant now = clock.instant();
        Duration wait = ownTransaction.execute(status -> {
            Duration longest = Duration.ZERO;
            for (Check check : checks) {
                Duration w = count(check, now);
                if (w.compareTo(longest) > 0) {
                    longest = w;
                }
            }
            return longest;
        });
        if (wait != null && !wait.isZero()) {
            throw new TooManyRequests("RATE_LIMITED", "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau.", wait);
        }
    }

    /** Returns how long to wait, or zero when this attempt is within the limit. */
    private Duration count(Check check, Instant now) {
        OffsetDateTime at = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        OffsetDateTime windowEdge = at.minus(check.limit().window());
        record Window(OffsetDateTime startedAt, int attempts) {}
        Window window = jdbc.sql("""
                insert into auth_rate_counters as c (action, scope, subject_digest, window_started_at, attempt_count)
                values (:action, :scope, :subject, :now, 1)
                on conflict (action, scope, subject_digest) do update set
                    window_started_at = case when c.window_started_at <= :edge then :now else c.window_started_at end,
                    attempt_count = case when c.window_started_at <= :edge then 1 else c.attempt_count + 1 end
                returning window_started_at, attempt_count
                """)
                .param("action", check.action().name())
                .param("scope", check.scope().name())
                .param("subject", check.subjectDigest())
                .param("now", at)
                .param("edge", windowEdge)
                .query((rs, n) -> new Window(rs.getObject(1, OffsetDateTime.class), rs.getInt(2)))
                .single();
        if (window.attempts() <= check.limit().attempts()) {
            return Duration.ZERO;
        }
        Duration wait = Duration.between(at, window.startedAt().plus(check.limit().window()));
        return wait.isNegative() || wait.isZero() ? Duration.ofSeconds(1) : wait;
    }
}
