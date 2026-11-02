package com.tutorbridge.platform.api;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records events in the caller's transaction, so they exist only if the business write commits. */
@Component
public class Outbox {

    private final JdbcClient jdbc;

    public Outbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns false when an event with this dedupe key already exists. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean publish(
            String aggregateType, UUID aggregateId, String eventType, Map<String, ?> payload, String dedupeKey) {
        return jdbc.sql("""
                insert into outbox_events (id, aggregate_type, aggregate_id, event_type, payload, dedupe_key)
                values (?, ?, ?, ?, cast(? as jsonb), ?)
                on conflict (dedupe_key) do nothing
                """).params(UUID.randomUUID(), aggregateType, aggregateId, eventType,
                        CanonicalJson.write(payload), dedupeKey).update() == 1;
    }
}
