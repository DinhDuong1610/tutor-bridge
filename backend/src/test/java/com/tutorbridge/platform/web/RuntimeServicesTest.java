package com.tutorbridge.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.LocalSeed;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

class RuntimeServicesTest extends HttpTest {

    @Autowired
    Clock clock;

    @Autowired
    IdGenerator ids;

    @Test
    void health_is_public_and_shows_only_the_status() throws Exception {
        for (String path : new String[] {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist());
        }
    }

    @Test
    void other_actuator_endpoints_are_closed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env").with(sessions().as(newActor(Actor.Role.ADMIN))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PASSWORD"))));
    }

    @Test
    void clock_is_utc_and_ids_are_unique() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(ids.generateId()).isNotEqualTo(ids.generateId());
    }

    @Test
    void local_seeds_run_in_order_each_inside_the_transaction_callback() {
        List<String> calls = new ArrayList<>();
        TransactionOperations recording = new TransactionOperations() {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                calls.add("begin");
                T result = action.doInTransaction(new org.springframework.transaction.support.SimpleTransactionStatus());
                calls.add("commit");
                return result;
            }
        };
        LocalSeed first = () -> calls.add("first");
        LocalSeed second = () -> calls.add("second");

        RuntimeServices.runSeeds(List.of(first, second), recording);

        assertThat(calls).containsExactly("begin", "first", "commit", "begin", "second", "commit");
    }
}
