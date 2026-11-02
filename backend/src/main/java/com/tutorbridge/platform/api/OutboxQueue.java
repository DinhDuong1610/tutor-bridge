package com.tutorbridge.platform.api;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The consuming side of the outbox. Delivery is at least once, so consumers dedupe (ADR-0003).
 *
 * <p>{@code attempts} is the fencing token: once a lease expires and the event is claimed again,
 * the previous worker can no longer finish it.
 */
@Component
public class OutboxQueue {

    public record ClaimedEvent(
            UUID id, String aggregateType, UUID aggregateId, String eventType, String payload,
            String dedupeKey, int attempts) {}

    private final JdbcClient jdbc;

    public OutboxQueue(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ClaimedEvent> claim(int limit, Duration lease) {
        return jdbc.sql("""
                update outbox_events
                set lease_until = now() + make_interval(secs => ?), attempts = attempts + 1
                where id in (
                    select id from outbox_events
                    where delivered_at is null and available_at <= now()
                      and (lease_until is null or lease_until < now())
                    order by available_at
                    limit ?
                    for update skip locked)
                returning id, aggregate_type, aggregate_id, event_type, payload::text as payload,
                          dedupe_key, attempts
                """).params((double) lease.toSeconds(), limit).query(ClaimedEvent.class).list();
    }

    /** Returns false when the lease was lost, in which case another worker owns the event. */
    public boolean markDelivered(ClaimedEvent event) {
        return jdbc.sql("""
                update outbox_events set delivered_at = now(), lease_until = null
                where id = ? and attempts = ? and delivered_at is null
                """).params(event.id(), event.attempts()).update() == 1;
    }

    /** Returns false when the lease was lost, in which case another worker owns the event. */
    public boolean retryAfter(ClaimedEvent event, Duration delay) {
        return jdbc.sql("""
                update outbox_events
                set available_at = now() + make_interval(secs => ?), lease_until = null
                where id = ? and attempts = ? and delivered_at is null
                """).params((double) delay.toSeconds(), event.id(), event.attempts()).update() == 1;
    }
}
