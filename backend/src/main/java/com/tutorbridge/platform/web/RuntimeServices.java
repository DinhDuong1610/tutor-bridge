package com.tutorbridge.platform.web;

import com.tutorbridge.platform.api.LocalSeed;
import com.tutorbridge.platform.api.PublicEndpoints;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;
import org.springframework.util.JdkIdGenerator;

/**
 * Runtime services shared by every module (ADR-0010 mục 6). New code takes time from {@link Clock}
 * and ids from {@link IdGenerator}, so tests can fix both.
 */
@Configuration(proxyBeanMethods = false)
class RuntimeServices {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeServices.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** UUIDv4 for now. Whether to move to UUIDv7 is open (docs/plan/m0-ban-giao.md); change it here. */
    @Bean
    IdGenerator idGenerator() {
        return new JdkIdGenerator();
    }

    /** Health for load balancers and orchestrators. Only the status is shown (management.* in application.yaml). */
    @Bean
    PublicEndpoints healthEndpoints() {
        return () -> List.of(
                new PublicEndpoints.Endpoint(HttpMethod.GET, "/actuator/health"),
                new PublicEndpoints.Endpoint(HttpMethod.GET, "/actuator/health/liveness"),
                new PublicEndpoints.Endpoint(HttpMethod.GET, "/actuator/health/readiness"));
    }

    @Bean
    @Profile("local")
    ApplicationRunner localSeeds(ObjectProvider<LocalSeed> seeds, TransactionOperations transactions) {
        return args -> runSeeds(seeds.orderedStream().toList(), transactions);
    }

    static void runSeeds(List<LocalSeed> seeds, TransactionOperations transactions) {
        for (LocalSeed seed : seeds) {
            transactions.executeWithoutResult(status -> seed.seed());
        }
        LOG.info("Local seed finished: {} seed(s)", seeds.size());
    }
}
