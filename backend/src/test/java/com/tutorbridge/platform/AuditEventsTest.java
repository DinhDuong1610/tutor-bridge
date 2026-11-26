package com.tutorbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.platform.api.AuditEvents;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.TraceContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class AuditEventsTest extends IntegrationTest {

    @Autowired
    AuditEvents audit;

    @Autowired
    CommandReceipts receipts;

    @Autowired
    TransactionTemplate tx;

    @AfterEach
    void clearTrace() {
        MDC.remove(TraceContext.MDC_KEY);
    }

    private UUID receipt(UUID actor) {
        return receipts.begin(actor, "TEST_COMMAND", UUID.randomUUID().toString(), Map.of("n", 1)).id();
    }

    private int rowsFor(UUID object) {
        return jdbc.sql("select count(*) from audit_events where object_id = ?").param(object)
                .query(Integer.class).single();
    }

    @Test
    void INV_21_an_event_is_written_with_its_command_and_the_current_trace() {
        UUID actor = newAccount();
        UUID object = UUID.randomUUID();
        MDC.put(TraceContext.MDC_KEY, "trace-audit-1");

        UUID command = tx.execute(s -> {
            UUID id = receipt(actor);
            audit.record(actor, id, "TEST_EVENT", "TEST_OBJECT", object, "DONE");
            return id;
        });

        assertThat(jdbc.sql("""
                select concat_ws('|', actor_id, command_id, event_type, object_type, outcome, trace_id)
                from audit_events where object_id = ?
                """).param(object).query(String.class).single())
                .isEqualTo(String.join("|", actor.toString(), command.toString(), "TEST_EVENT", "TEST_OBJECT", "DONE",
                        "trace-audit-1"));
    }

    @Test
    void INV_21_an_event_rolls_back_with_its_command() {
        UUID actor = newAccount();
        UUID object = UUID.randomUUID();
        MDC.put(TraceContext.MDC_KEY, "trace-audit-2");

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            audit.record(actor, receipt(actor), "TEST_EVENT", "TEST_OBJECT", object, "DONE");
            throw new IllegalStateException("the command fails after the audit row");
        })).hasMessageContaining("the command fails");

        assertThat(rowsFor(object)).isZero();
    }

    @Test
    void an_event_is_only_written_inside_the_command_transaction() {
        MDC.put(TraceContext.MDC_KEY, "trace-audit-3");
        UUID object = UUID.randomUUID();

        assertThatThrownBy(() -> audit.record(newAccount(), null, "TEST_EVENT", "TEST_OBJECT", object, "DONE"))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(rowsFor(object)).isZero();
    }

    @Test
    void an_event_without_a_request_trace_is_refused() {
        UUID actor = newAccount();
        UUID object = UUID.randomUUID();

        assertThatThrownBy(() -> tx.executeWithoutResult(
                s -> audit.record(actor, receipt(actor), "TEST_EVENT", "TEST_OBJECT", object, "DONE")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(rowsFor(object)).isZero();
    }
}
