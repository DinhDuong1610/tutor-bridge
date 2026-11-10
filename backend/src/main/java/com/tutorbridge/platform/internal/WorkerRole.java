package com.tutorbridge.platform.internal;

import com.tutorbridge.platform.api.OutboxHandler;
import com.tutorbridge.platform.api.OutboxQueue;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The worker role of docs/design/kien-truc.md: same artifact, switched on with
 * {@code tutorbridge.worker.enabled=true}. Off by default, so an API instance never sends email.
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
