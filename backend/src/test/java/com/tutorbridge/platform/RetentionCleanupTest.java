package com.tutorbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.platform.internal.RetentionCleanup;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The worker's retention cleanup on the real tables (ADR-0006, INV-26, #24). */
class RetentionCleanupTest extends IntegrationTest {

    private static final Duration KEEP = Duration.ofDays(7);

    private UUID actor;

    @BeforeEach
    void onlyThisTestsOldRows() {
        // Rows other tests leave behind are never old, but start from a known state anyway.
        jdbc.sql("delete from command_receipts where expires_at < now()").update();
        jdbc.sql("delete from outbox_events where delivered_at is not null").update();
        actor = newAccount();
    }

    private RetentionCleanup cleanup(int batchSize, int maxBatches) {
        return new RetentionCleanup(jdbc, batchSize, maxBatches, KEEP);
    }

    /** A receipt created {@code ageDays} ago that expired 7 days after that. */
    private UUID receipt(String state, int ageDays) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into command_receipts
                    (id, actor_id, command_type, idempotency_key, payload_sha256, state, created_at, expires_at)
                values (?, ?, 'TEST', ?, ?, ?, now() - make_interval(days => ?),
                        now() - make_interval(days => ?) + interval '7 days')
                """).params(id, actor, "key-" + id, "0".repeat(64), state, ageDays, ageDays).update();
        return id;
    }

    /** An outbox event delivered {@code deliveredDaysAgo} days ago, or never when null. */
    private UUID event(Integer deliveredDaysAgo) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into outbox_events (id, aggregate_type, aggregate_id, event_type, payload, dedupe_key,
                                           created_at, available_at, delivered_at)
                values (?, 'TEST', ?, 'TEST', '{}'::jsonb, ?, now() - interval '30 days', now() - interval '30 days',
                        case when ? then now() - make_interval(days => ?) end)
                """).params(id, UUID.randomUUID(), "dedupe-" + id, deliveredDaysAgo != null,
                        deliveredDaysAgo == null ? 0 : deliveredDaysAgo).update();
        return id;
    }

    private boolean receiptExists(UUID id) {
        return jdbc.sql("select count(*) from command_receipts where id = ?").param(id).query(Integer.class).single() == 1;
    }

    private boolean eventExists(UUID id) {
        return jdbc.sql("select count(*) from outbox_events where id = ?").param(id).query(Integer.class).single() == 1;
    }

    @Test
    void INV_26_expired_finished_receipts_go_and_live_ones_stay() {
        UUID succeeded = receipt("SUCCEEDED", 8);
        UUID failed = receipt("FAILED", 30);
        UUID live = receipt("SUCCEEDED", 6);

        RetentionCleanup.Result result = cleanup(100, 10).runOnce();

        assertThat(receiptExists(succeeded)).isFalse();
        assertThat(receiptExists(failed)).isFalse();
        assertThat(receiptExists(live)).isTrue();
        assertThat(result.receiptsDeleted()).isEqualTo(2);
    }

    @Test
    void an_expired_receipt_still_processing_is_kept_and_counted_as_stuck() {
        UUID stuck = receipt("PROCESSING", 8);
        UUID working = receipt("PROCESSING", 1);

        RetentionCleanup.Result result = cleanup(100, 10).runOnce();

        assertThat(receiptExists(stuck)).isTrue();
        assertThat(receiptExists(working)).isTrue();
        assertThat(result.receiptsDeleted()).isZero();
        assertThat(result.stuckReceipts()).isEqualTo(1);
    }

    @Test
    void a_delivered_event_goes_seven_days_after_delivery_and_an_undelivered_one_never() {
        UUID old = event(8);
        UUID recent = event(6);
        UUID undelivered = event(null);

        RetentionCleanup.Result result = cleanup(100, 10).runOnce();

        assertThat(eventExists(old)).isFalse();
        assertThat(eventExists(recent)).isTrue();
        assertThat(eventExists(undelivered)).isTrue();
        assertThat(result.outboxDeleted()).isEqualTo(1);
    }

    @Test
    void one_run_deletes_at_most_its_batches_and_the_next_run_takes_the_rest() {
        for (int i = 0; i < 5; i++) {
            receipt("SUCCEEDED", 10 + i);
            event(10 + i);
        }

        RetentionCleanup.Result first = cleanup(2, 2).runOnce();
        RetentionCleanup.Result second = cleanup(2, 2).runOnce();

        assertThat(first.receiptsDeleted()).isEqualTo(4);
        assertThat(first.outboxDeleted()).isEqualTo(4);
        assertThat(second.receiptsDeleted()).isEqualTo(1);
        assertThat(second.outboxDeleted()).isEqualTo(1);
    }

    @Test
    void INV_26_records_that_named_a_deleted_receipt_or_event_stay() {
        UUID receipt = receipt("SUCCEEDED", 8);
        UUID event = event(8);
        UUID recipient = newAccount();
        jdbc.sql("""
                insert into audit_events (id, actor_id, command_id, event_type, object_type, object_id, outcome, trace_id)
                values (?, ?, ?, 'TEST', 'TEST', ?, 'OK', 'trace')
                """).params(UUID.randomUUID(), actor, receipt, UUID.randomUUID()).update();
        jdbc.sql("""
                insert into notifications (id, recipient_id, event_id, template_code)
                values (?, ?, ?, 'TEST')
                """).params(UUID.randomUUID(), recipient, event).update();

        cleanup(100, 10).runOnce();

        assertThat(receiptExists(receipt)).isFalse();
        assertThat(eventExists(event)).isFalse();
        assertThat(jdbc.sql("select count(*) from audit_events where command_id = ?").param(receipt)
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from notifications where event_id = ?").param(event)
                .query(Integer.class).single()).isEqualTo(1);
    }
}
