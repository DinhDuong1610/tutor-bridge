package com.tutorbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.platform.api.Outbox;
import com.tutorbridge.platform.api.OutboxQueue;
import com.tutorbridge.platform.api.OutboxQueue.ClaimedEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxTest extends IntegrationTest {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Autowired
    Outbox outbox;

    @Autowired
    OutboxQueue queue;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void emptyQueue() {
        jdbc.sql("delete from outbox_events").update();
    }

    private boolean publish(String dedupeKey) {
        return tx.execute(s -> outbox.publish(
                "CLASS", UUID.randomUUID(), "CLASS_CREATED", Map.of("classId", "c-1"), dedupeKey));
    }

    private void expireLease(ClaimedEvent event) {
        jdbc.sql("update outbox_events set lease_until = now() - interval '1 second' where id = ?")
                .param(event.id()).update();
    }

    @Test
    void duplicate_dedupe_key_creates_one_event() {
        assertThat(publish("class-created-1")).isTrue();
        assertThat(publish("class-created-1")).isFalse();

        assertThat(queue.claim(10, LEASE)).hasSize(1);
    }

    @Test
    void claimed_event_carries_its_payload_and_is_hidden_while_leased() {
        publish("event-1");

        List<ClaimedEvent> claimed = queue.claim(10, LEASE);

        assertThat(claimed).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("CLASS_CREATED");
            assertThat(event.payload()).contains("c-1");
            assertThat(event.attempts()).isEqualTo(1);
        });
        assertThat(queue.claim(10, LEASE)).isEmpty();
    }

    @Test
    void concurrent_claims_never_share_an_event() {
        IntStream.range(0, 40).forEach(i -> publish("event-" + i));

        var claims = IntStream.range(0, 4)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> tx.execute(s -> queue.claim(40, LEASE))))
                .toList();
        var ids = claims.stream().map(CompletableFuture::join).flatMap(List::stream).map(ClaimedEvent::id).toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void delivered_event_is_never_claimed_again() {
        publish("event-1");
        ClaimedEvent event = queue.claim(1, LEASE).getFirst();

        assertThat(queue.markDelivered(event)).isTrue();
        expireLease(event);

        assertThat(queue.claim(10, LEASE)).isEmpty();
    }

    @Test
    void expired_lease_is_reclaimed_and_the_old_worker_is_fenced_off() {
        publish("event-1");
        ClaimedEvent stale = queue.claim(1, LEASE).getFirst();
        expireLease(stale);

        ClaimedEvent current = queue.claim(1, LEASE).getFirst();

        assertThat(current.id()).isEqualTo(stale.id());
        assertThat(current.attempts()).isEqualTo(2);
        assertThat(queue.markDelivered(stale)).isFalse();
        assertThat(queue.retryAfter(stale, Duration.ZERO)).isFalse();
        assertThat(queue.markDelivered(current)).isTrue();
    }

    @Test
    void retry_delay_hides_the_event_until_it_is_due() {
        publish("event-1");
        ClaimedEvent event = queue.claim(1, LEASE).getFirst();

        assertThat(queue.retryAfter(event, Duration.ofHours(1))).isTrue();
        assertThat(queue.claim(10, LEASE)).isEmpty();

        jdbc.sql("update outbox_events set available_at = now() where id = ?").param(event.id()).update();
        assertThat(queue.claim(10, LEASE)).singleElement()
                .satisfies(again -> assertThat(again.attempts()).isEqualTo(2));
    }
}
