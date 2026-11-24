package com.tutorbridge.classes.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * TB-18 Q1: PENDING offers past min(sent + 48 h, first session) become EXPIRED, a batch per transaction,
 * with the OFFER_EXPIRED event TB-21 tells the parent from. Reads already show such an offer as EXPIRED
 * before this runs, so the delay of one run is never visible.
 *
 * <p>Each batch takes only the offer rows, SKIP LOCKED: an offer that a reject, a cancel or an accept
 * (TX05, need then offer) holds is left for the next run, so the job never waits on nor deadlocks with them.
 */
@Component
class OfferExpiry {

    private final OfferRepository offers;
    private final OfferService service;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final int batchSize;
    private final int maxBatches;

    OfferExpiry(OfferRepository offers, OfferService service, TransactionOperations transactions, Clock clock,
            @Value("${tutorbridge.worker.offer-expiry.batch-size:100}") int batchSize,
            @Value("${tutorbridge.worker.offer-expiry.max-batches:20}") int maxBatches) {
        if (batchSize < 1 || maxBatches < 1) {
            throw new IllegalArgumentException("batch size and max batches must be positive");
        }
        this.offers = offers;
        this.service = service;
        this.transactions = transactions;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
    }

    /** One run: batches until one comes back short or {@code maxBatches} ran. Returns how many expired. */
    int runOnce() {
        int expired = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            List<UUID> due = transactions.execute(status -> {
                List<UUID> ids = offers.lockOverdue(now, batchSize);
                ids.forEach(id -> service.expire(id, now));
                return ids;
            });
            expired += due.size();
            if (due.size() < batchSize) {
                break;
            }
        }
        return expired;
    }

    /** Only the worker role runs it, as the outbox and the retention cleanup (docs/design/kien-truc.md). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty("tutorbridge.worker.enabled")
    static class Schedule {

        @Bean
        Runner offerExpiryRunner(OfferExpiry expiry) {
            return new Runner(expiry);
        }
    }

    static class Runner {

        private final OfferExpiry expiry;

        Runner(OfferExpiry expiry) {
            this.expiry = expiry;
        }

        @Scheduled(initialDelayString = "${tutorbridge.worker.offer-expiry.interval:PT1M}",
                fixedDelayString = "${tutorbridge.worker.offer-expiry.interval:PT1M}")
        void run() {
            expiry.runOnce();
        }
    }
}
