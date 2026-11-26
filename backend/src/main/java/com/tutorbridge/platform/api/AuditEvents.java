package com.tutorbridge.platform.api;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code audit_events}: who did what to which object, in the same commit as the change it records
 * (INV-21). Joins the caller's transaction, so a rolled back command leaves no audit row behind.
 *
 * <p>The trace id is the current request's (see {@link TraceContext}), never one the caller makes up, so
 * an audit row always leads back to the request's logs. Recording outside a request is refused until a
 * job needs it.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AuditEvents {

    private final JdbcClient jdbc;

    public AuditEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param actorId the account that sent the command
     * @param commandId the command's receipt, so the row ties to one idempotent command
     */
    public void record(UUID actorId, UUID commandId, String eventType, String objectType, UUID objectId,
            String outcome) {
        String traceId = TraceContext.current();
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalStateException("An audit event needs the trace id of the current request");
        }
        jdbc.sql("""
                insert into audit_events(id, actor_id, command_id, event_type, object_type, object_id, outcome, trace_id)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """).params(UUID.randomUUID(), actorId, commandId, eventType, objectType, objectId, outcome, traceId)
                .update();
    }
}
