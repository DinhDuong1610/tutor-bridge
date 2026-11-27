package com.tutorbridge.identity.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * #182: removes evidence bytes nothing can use any more, so the local {@link ObjectStore} does not fill up.
 *
 * <ol>
 *   <li><b>Uploads.</b> UPLOADING past its ticket by {@code expiredKeep}, and FAILED older than
 *       {@code failedKeep}, become DELETED, a batch per short transaction (SKIP LOCKED); their bytes are
 *       removed after the commit, outside any transaction (INV-18). Once a ticket has expired, neither a
 *       PUT nor a complete can change the upload or its bytes, so nothing races the job.</li>
 *   <li><b>Orphans.</b> Bytes in {@code objects} whose key no row in use names, written more than
 *       {@code orphanKeep} ago: a PUT the database then refused, or step 1 stopped between its commit and
 *       the removal. The age keeps the bytes of a PUT still on its way to its row.</li>
 *   <li><b>Staging.</b> Files in {@code tmp} older than {@code orphanKeep}, left by a process that died
 *       mid-PUT.</li>
 * </ol>
 *
 * <p>Never touched: READY, QUARANTINED, and any upload linked to a profile version (TB-12c, V11). Every step
 * can run again after a crash, and several worker instances can run it at once. The log names no key or
 * path (AC-UC03-03), only counts.
 *
 * <p>The keep times are TBD (#182 Q1, Đính): the defaults below are the agent's proposal on #182.
 */
@Component
class EvidenceCleanup {

    private static final Logger LOG = LoggerFactory.getLogger(EvidenceCleanup.class);

    /** What one run did; {@code failures} counts bytes it could not remove, left for the next run. */
    record Result(int uploadsDeleted, int orphansDeleted, int stagedDeleted, int failures) {}

    private final EvidenceUploadRepository uploads;
    private final ObjectStore store;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final int batchSize;
    private final int maxBatches;
    private final Duration expiredKeep;
    private final Duration failedKeep;
    private final Duration orphanKeep;

    EvidenceCleanup(EvidenceUploadRepository uploads, ObjectStore store, TransactionOperations transactions,
            Clock clock,
            @Value("${tutorbridge.worker.evidence-cleanup.batch-size:100}") int batchSize,
            @Value("${tutorbridge.worker.evidence-cleanup.max-batches:20}") int maxBatches,
            // TBD #182 Q1 (Đính): proposal 24 h past the ticket, 7 days for FAILED, 24 h for orphans.
            @Value("${tutorbridge.worker.evidence-cleanup.expired-keep:PT24H}") Duration expiredKeep,
            @Value("${tutorbridge.worker.evidence-cleanup.failed-keep:P7D}") Duration failedKeep,
            @Value("${tutorbridge.worker.evidence-cleanup.orphan-keep:PT24H}") Duration orphanKeep) {
        if (batchSize < 1 || maxBatches < 1) {
            throw new IllegalArgumentException("batch size and max batches must be positive");
        }
        // A PUT checks its ticket just before it moves the bytes in: no keep time may be zero.
        for (Duration keep : List.of(expiredKeep, failedKeep, orphanKeep)) {
            if (keep.isNegative() || keep.isZero()) {
                throw new IllegalArgumentException("evidence keep times must be positive");
            }
        }
        this.uploads = uploads;
        this.store = store;
        this.transactions = transactions;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
        this.expiredKeep = expiredKeep;
        this.failedKeep = failedKeep;
        this.orphanKeep = orphanKeep;
    }

    Result runOnce() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int[] failures = {0};
        int uploadsDeleted = deleteUnusedUploads(now, failures);
        int orphansDeleted = deleteOrphans(now.minus(orphanKeep), failures);
        int stagedDeleted;
        try {
            stagedDeleted = store.deleteStagedBefore(now.minus(orphanKeep));
        } catch (IOException e) {
            failures[0]++;
            stagedDeleted = 0;
        }
        Result result = new Result(uploadsDeleted, orphansDeleted, stagedDeleted, failures[0]);
        if (uploadsDeleted + orphansDeleted + stagedDeleted + failures[0] > 0) {
            LOG.info("Evidence cleanup: {} uploads, {} orphans, {} staged files removed, {} left for the next run",
                    uploadsDeleted, orphansDeleted, stagedDeleted, failures[0]);
        }
        return result;
    }

    private int deleteUnusedUploads(Instant now, int[] failures) {
        int deleted = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            List<String> keys = transactions.execute(status ->
                    uploads.markUnusedDeleted(now.minus(expiredKeep), now.minus(failedKeep), batchSize));
            for (String key : keys) {
                // Bytes left here are an orphan now, so the sweep below or a later run removes them.
                if (remove(key)) {
                    deleted++;
                } else {
                    failures[0]++;
                }
            }
            if (keys.size() < batchSize) {
                break;
            }
        }
        return deleted;
    }

    private int deleteOrphans(Instant writtenBefore, int[] failures) {
        int deleted = 0;
        try (Stream<String> keys = store.keysWrittenBefore(writtenBefore)) {
            List<String> chunk = new ArrayList<>(batchSize);
            for (String key : (Iterable<String>) keys::iterator) {
                chunk.add(key);
                if (chunk.size() == batchSize) {
                    deleted += deleteUnused(chunk, failures);
                    chunk.clear();
                }
            }
            deleted += deleteUnused(chunk, failures);
        } catch (IOException | UncheckedIOException e) {
            failures[0]++;
        }
        return deleted;
    }

    /** The keys are checked against the database after they were listed, so a row in use always wins. */
    private int deleteUnused(List<String> keys, int[] failures) {
        Set<String> inUse = uploads.inUse(keys);
        int deleted = 0;
        for (String key : keys) {
            if (inUse.contains(key)) {
                continue;
            }
            if (remove(key)) {
                deleted++;
            } else {
                failures[0]++;
            }
        }
        return deleted;
    }

    /** Windows refuses to remove a file a reader holds open; such bytes wait for the next run. */
    private boolean remove(String key) {
        try {
            store.delete(key);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Only the worker role runs it, as the outbox and the retention cleanup (docs/design/kien-truc.md). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty("tutorbridge.worker.enabled")
    static class Schedule {

        @Bean
        Runner evidenceCleanupRunner(EvidenceCleanup cleanup) {
            return new Runner(cleanup);
        }
    }

    static class Runner {

        private final EvidenceCleanup cleanup;

        Runner(EvidenceCleanup cleanup) {
            this.cleanup = cleanup;
        }

        @Scheduled(initialDelayString = "${tutorbridge.worker.evidence-cleanup.interval:PT1H}",
                fixedDelayString = "${tutorbridge.worker.evidence-cleanup.interval:PT1H}")
        void run() {
            cleanup.runOnce();
        }
    }
}
