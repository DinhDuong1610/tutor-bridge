package com.tutorbridge.platform.api;

/**
 * Consumes one outbox event type on the worker role (ADR-0003, docs/design/kien-truc.md). A module
 * declares its handlers as beans; the dispatcher in platform claims events and calls them.
 *
 * <p>{@link #handle} runs outside any database transaction, so it may call external services
 * (INV-18). Delivery is at least once: a handler can see the same event twice and must tolerate it.
 * Throwing schedules a retry with backoff. Never put secrets or personal data in the exception
 * message; the dispatcher logs only the exception type.
 */
public interface OutboxHandler {

    /** The {@code outbox_events.event_type} this handler consumes. One handler per type. */
    String eventType();

    void handle(OutboxQueue.ClaimedEvent event) throws Exception;
}
