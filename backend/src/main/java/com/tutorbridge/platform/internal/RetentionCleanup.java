package com.tutorbridge.platform.internal;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Worker role: deletes expired command receipts and delivered outbox events in small batches
 * (ADR-0006, INV-26, #24). V6 dropped every foreign key to these two tables so they can shrink;
 * this job is what actually shrinks them.
 *
 * <p>Rules (Đính, #24, 21/11/2026):
 * <ul>
 *   <li>A receipt goes once {@code expires_at} has passed (at least 7 days, G3-DP01), unless it is
 *       still PROCESSING: an expired PROCESSING receipt means a stuck command, so it is kept for a
 *       person to look at and only counted in the log. Deleting it would let the same key run the
 *       command again.</li>
 *   <li>An outbox event goes {@code outboxKeep} (7 days) after it was delivered, the same window
 *       as a receipt, so a command sent again with its key still meets the old dedupe key. An event
 *       not yet delivered is never deleted.</li>
 * </ul>
 *
 * <p>Each batch is its own short statement with {@code skip locked}, so the job never holds locks
 * for long and several worker instances can run it at once. One run stops after
 * {@code maxBatches} per table; what is left waits for the next run.
 */
public class RetentionCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(RetentionCleanup.class);

    /** What one run did. {@code stuckReceipts} counts expired receipts kept because PROCESSING. */
    public record Result(int receiptsDeleted, int outboxDeleted, long stuckReceipts) {}

    private final JdbcClient jdbc;
    private final int batchSize;
    private final int maxBatches;
    private final Duration outboxKeep;

    public RetentionCleanup(JdbcClient jdbc, int batchSize, int maxBatches, Duration outboxKeep) {
        if (batchSize < 1 || maxBatches < 1 || outboxKeep.isNegative()) {
            throw new IllegalArgumentException("cleanup needs a positive batch size, batch count and keep time");
        }
        this.jdbc = jdbc;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
        this.outboxKeep = outboxKeep;
    }

    public Result runOnce() {
        int receipts = repeat(this::deleteExpiredReceipts);
        int outbox = repeat(this::deleteDeliveredOutbox);
        long stuck = jdbc.sql("""
                select count(*) from command_receipts where expires_at < now() and state = 'PROCESSING'
                """).query(Long.class).single();
        if (receipts > 0 || outbox > 0) {
            LOG.info("Retention cleanup deleted {} expired receipts and {} delivered outbox events", receipts, outbox);
        }
        if (stuck > 0) {
            // Counts only: keys and payloads never reach the log.
            LOG.warn("Retention cleanup kept {} expired receipts still PROCESSING; their commands look stuck", stuck);
        }
        return new Result(receipts, outbox, stuck);
    }

    private int repeat(java.util.function.IntSupplier batch) {
        int total = 0;
        for (int i = 0; i < maxBatches; i++) {
            int deleted = batch.getAsInt();
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }

    private int deleteExpiredReceipts() {
        return jdbc.sql("""
                delete from command_receipts where id in (
                    select id from command_receipts
                    where expires_at < now() and state <> 'PROCESSING'
                    order by expires_at
                    limit ?
                    for update skip locked)
                """).param(batchSize).update();
    }

    private int deleteDeliveredOutbox() {
        return jdbc.sql("""
                delete from outbox_events where id in (
                    select id from outbox_events
                    where delivered_at is not null and delivered_at < now() - make_interval(secs => ?)
                    order by delivered_at
                    limit ?
                    for update skip locked)
                """).params((double) outboxKeep.toSeconds(), batchSize).update();
    }
}
