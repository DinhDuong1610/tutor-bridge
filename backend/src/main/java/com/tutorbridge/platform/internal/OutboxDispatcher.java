package com.tutorbridge.platform.internal;

import com.tutorbridge.platform.api.OutboxHandler;
import com.tutorbridge.platform.api.OutboxQueue;
import com.tutorbridge.platform.api.OutboxQueue.ClaimedEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker role: claims due outbox events and hands each to the handler of its type, outside any
 * transaction. Success marks the event delivered; failure retries with exponential backoff, capped
 * at one hour. Events with no handler on this instance are postponed, not dropped.
 *
 * <p>Several instances may run at once: {@link OutboxQueue#claim} never gives one event to two of
 * them, and its fencing token stops a worker whose lease expired from finishing the event.
 */
public class OutboxDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcher.class);
    static final Duration FIRST_RETRY = Duration.ofSeconds(30);
    static final Duration MAX_RETRY = Duration.ofHours(1);
    static final Duration NO_HANDLER_DELAY = Duration.ofMinutes(10);

    private final OutboxQueue queue;
    private final Map<String, OutboxHandler> handlers;
    private final int batchSize;
    private final Duration lease;

    public OutboxDispatcher(OutboxQueue queue, List<OutboxHandler> handlers, int batchSize, Duration lease) {
        this.queue = queue;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                OutboxHandler::eventType, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("two outbox handlers for " + a.eventType());
                }));
        this.batchSize = batchSize;
        this.lease = lease;
    }

    /** Claims and handles one batch. Returns how many events were delivered. */
    public int dispatchOnce() {
        int delivered = 0;
        for (ClaimedEvent event : queue.claim(batchSize, lease)) {
            OutboxHandler handler = handlers.get(event.eventType());
            if (handler == null) {
                queue.retryAfter(event, NO_HANDLER_DELAY);
                LOG.warn("Outbox event {} has no handler for type {}", event.id(), event.eventType());
                continue;
            }
            try {
                handler.handle(event);
            } catch (Exception e) {
                queue.retryAfter(event, backoff(event.attempts()));
                LOG.warn("Outbox event {} of type {} failed on attempt {}: {}", event.id(), event.eventType(),
                        event.attempts(), e.getClass().getName());
                continue;
            }
            if (queue.markDelivered(event)) {
                delivered++;
            } else {
                LOG.warn("Outbox event {} lost its lease before it was marked delivered", event.id());
            }
        }
        return delivered;
    }

    /** 30 s, 60 s, 120 s, … capped at one hour. {@code attempts} starts at 1 on the first claim. */
    static Duration backoff(int attempts) {
        int doublings = Math.min(Math.max(attempts - 1, 0), 20);
        Duration delay = FIRST_RETRY.multipliedBy(1L << doublings);
        return delay.compareTo(MAX_RETRY) > 0 ? MAX_RETRY : delay;
    }
}
