package com.tutorbridge.platform.internal;

import com.tutorbridge.platform.api.OutboxHandler;
import com.tutorbridge.platform.api.OutboxQueue;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The worker role of docs/design/kien-truc.md: same artifact, switched on with
 * {@code tutorbridge.worker.enabled=true}. Off by default, so an API instance never sends email.
 * It also runs the retention cleanup of receipts and outbox events (#24).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("tutorbridge.worker.enabled")
@EnableScheduling
class WorkerRole {

    @Bean
    OutboxDispatcher outboxDispatcher(OutboxQueue queue, ObjectProvider<OutboxHandler> handlers,
            @Value("${tutorbridge.worker.batch-size:20}") int batchSize,
            @Value("${tutorbridge.worker.lease:PT1M}") Duration lease) {
        return new OutboxDispatcher(queue, handlers.orderedStream().toList(), batchSize, lease);
    }

    @Bean
    OutboxPoller outboxPoller(OutboxDispatcher dispatcher) {
        return new OutboxPoller(dispatcher);
    }

    @Bean
    RetentionCleanup retentionCleanup(JdbcClient jdbc,
            @Value("${tutorbridge.worker.cleanup.batch-size:500}") int batchSize,
            @Value("${tutorbridge.worker.cleanup.max-batches:40}") int maxBatches,
            @Value("${tutorbridge.worker.cleanup.outbox-keep:P7D}") Duration outboxKeep) {
        return new RetentionCleanup(jdbc, batchSize, maxBatches, outboxKeep);
    }

    @Bean
    CleanupSchedule cleanupSchedule(RetentionCleanup cleanup) {
        return new CleanupSchedule(cleanup);
    }

    static class CleanupSchedule {

        private final RetentionCleanup cleanup;

        CleanupSchedule(RetentionCleanup cleanup) {
            this.cleanup = cleanup;
        }

        @Scheduled(initialDelayString = "${tutorbridge.worker.cleanup.interval:PT1H}",
                fixedDelayString = "${tutorbridge.worker.cleanup.interval:PT1H}")
        void run() {
            cleanup.runOnce();
        }
    }

    static class OutboxPoller {

        private final OutboxDispatcher dispatcher;

        OutboxPoller(OutboxDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Scheduled(initialDelayString = "${tutorbridge.worker.poll-interval:PT2S}",
                fixedDelayString = "${tutorbridge.worker.poll-interval:PT2S}")
        void poll() {
            dispatcher.dispatchOnce();
        }
    }
}
