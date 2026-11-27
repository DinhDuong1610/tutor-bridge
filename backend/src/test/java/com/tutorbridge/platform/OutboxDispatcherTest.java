package com.tutorbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.platform.api.Outbox;
import com.tutorbridge.platform.api.OutboxHandler;
import com.tutorbridge.platform.api.OutboxQueue;
import com.tutorbridge.platform.api.OutboxQueue.ClaimedEvent;
import com.tutorbridge.platform.internal.OutboxDispatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The worker role's dispatch loop on the real outbox (ADR-0003). */
class OutboxDispatcherTest extends IntegrationTest {

    private static final Duration LEASE = Duration.ofMinutes(1);

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

    @Test
    void a_handled_event_is_delivered_once_and_the_handler_runs_outside_any_transaction() {
        UUID id = publish("TEST_DONE");
        Recorder handler = new Recorder("TEST_DONE", false);

        assertThat(dispatcher(handler).dispatchOnce()).isEqualTo(1);
        assertThat(dispatcher(handler).dispatchOnce()).isZero();

        assertThat(handler.seen).containsExactly(id);
        assertThat(handler.inTransaction).containsExactly(false);
        assertThat(jdbc.sql("select delivered_at is not null from outbox_events where aggregate_id = ?")
                .param(id).query(Boolean.class).single()).isTrue();
    }

    @Test
    void a_failing_handler_leaves_the_event_undelivered_and_retries_it_later_with_backoff() {
        UUID id = publish("TEST_FAILS");
        Recorder handler = new Recorder("TEST_FAILS", true);

        assertThat(dispatcher(handler).dispatchOnce()).isZero();

        Map<String, Object> row = jdbc.sql("""
                select delivered_at is null as pending, lease_until is null as released,
                       available_at > now() + interval '25 seconds' as later, attempts
                from outbox_events where aggregate_id = ?
                """).param(id).query().singleRow();
        assertThat(row).containsEntry("pending", true).containsEntry("released", true)
                .containsEntry("later", true).containsEntry("attempts", 1);
        assertThat(dispatcher(handler).dispatchOnce()).isZero();
        assertThat(handler.seen).containsExactly(id);
    }

    @Test
    void an_event_without_a_handler_is_postponed_not_dropped() {
        UUID id = publish("TEST_NOBODY");

        assertThat(dispatcher(new Recorder("TEST_OTHER", false)).dispatchOnce()).isZero();

        assertThat(jdbc.sql("""
                select delivered_at is null and available_at > now() + interval '9 minutes'
                from outbox_events where aggregate_id = ?
                """).param(id).query(Boolean.class).single()).isTrue();
    }

    @Test
    void two_handlers_for_one_type_are_refused_at_startup() {
        assertThatThrownBy(() -> new OutboxDispatcher(queue,
                List.of(new Recorder("TEST_TWICE", false), new Recorder("TEST_TWICE", false)), 10, LEASE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TEST_TWICE");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void Q3_a_failure_is_retried_without_a_cap_and_warns_from_the_tenth_attempt(CapturedOutput output) {
        UUID early = publish("TEST_FAILS");
        UUID late = publish("TEST_FAILS");
        // Nine claims already happened to the late one; this claim is its tenth.
        jdbc.sql("update outbox_events set attempts = 9 where aggregate_id = ?").param(late).update();
        Recorder handler = new Recorder("TEST_FAILS", true);

        assertThat(dispatcher(handler).dispatchOnce()).isZero();

        String earlyId = jdbc.sql("select id::text from outbox_events where aggregate_id = ?").param(early)
                .query(String.class).single();
        String lateId = jdbc.sql("select id::text from outbox_events where aggregate_id = ?").param(late)
                .query(String.class).single();
        assertThat(output.getOut()).contains("Outbox event " + lateId + " of type TEST_FAILS still fails after 10 attempts")
                .contains("Outbox event " + earlyId + " of type TEST_FAILS failed on attempt 1")
                .doesNotContain("Outbox event " + earlyId + " of type TEST_FAILS still fails");
        // No cap: the late event is still waiting for its next try, not dropped or marked failed.
        assertThat(jdbc.sql("select delivered_at is null and attempts = 10 from outbox_events where aggregate_id = ?")
                .param(late).query(Boolean.class).single()).isTrue();
    }

    private OutboxDispatcher dispatcher(OutboxHandler handler) {
        return new OutboxDispatcher(queue, List.of(handler), 10, LEASE);
    }

    private UUID publish(String type) {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> outbox.publish("TEST", id, type, Map.of("n", 1), type + ":" + id));
        return id;
    }

    private static final class Recorder implements OutboxHandler {

        final String type;
        final boolean fail;
        final List<UUID> seen = new ArrayList<>();
        final List<Boolean> inTransaction = new ArrayList<>();

        Recorder(String type, boolean fail) {
            this.type = type;
            this.fail = fail;
        }

        @Override
        public String eventType() {
            return type;
        }

        @Override
        public void handle(ClaimedEvent event) {
            seen.add(event.aggregateId());
            inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (fail) {
                throw new IllegalStateException("smtp unavailable");
            }
        }
    }
}
