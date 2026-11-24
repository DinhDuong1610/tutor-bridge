package com.tutorbridge.platform.api;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The in-app inbox of UC15 (TX30, TB-21): a module's outbox handler puts one notification per event and
 * recipient here. A notification carries only the event type and its target (type, id, version), never
 * private content (UC15); opening the target checks access again where the target is read.
 */
@Component
public class Notifications {

    private final JdbcClient jdbc;

    public Notifications(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Delivers one notification of {@code event} to {@code recipientId}. Delivery is at least once, so a
     * second call for the same event and recipient adds nothing (UNIQUE(recipient_id, event_id), AC-UC15-02).
     * Returns whether a notification was added.
     */
    public boolean deliver(UUID recipientId, UUID eventId, String eventType, String targetType, UUID targetId,
            String targetVersion) {
        return jdbc.sql("""
                insert into notifications(id, recipient_id, event_id, template_code, target_type, target_id,
                                          target_version)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (recipient_id, event_id) do nothing
                """).params(UUID.randomUUID(), recipientId, eventId, eventType, targetType, targetId, targetVersion)
                .update() == 1;
    }
}
