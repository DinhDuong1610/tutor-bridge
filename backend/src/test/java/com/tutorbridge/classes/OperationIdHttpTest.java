package com.tutorbridge.classes;

import static com.tutorbridge.classes.OfferHttpFixture.id;
import static com.tutorbridge.classes.OfferHttpFixture.input;
import static com.tutorbridge.classes.OfferHttpFixture.reason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.classes.OfferHttpFixture.Scenario;
import com.tutorbridge.platform.api.Actor;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #221: every write on {@code dev} whose answer declares {@code X-Operation-Id} sends it. It is the id of
 * the caller's receipt: a replay with the same key answers the same id, and getCommandStatus by that id
 * answers that very command (TX16, BR06, INV-24). Fake accounts and texts only.
 */
class OperationIdHttpTest extends HttpTest {

    private static final String HEADER = "X-Operation-Id";
    private static final String GOAL = "Nắm chắc phương trình bậc nhất một ẩn trước kỳ kiểm tra giữa kỳ.";

    @Autowired
    TransactionTemplate tx;

    private OfferHttpFixture fx;

    @BeforeEach
    void fixture() {
        fx = new OfferHttpFixture(mvc, jdbc, tx, a -> sessions().as(a), this::newActor);
    }

    private static String draft(LocalDate start, int budget) {
        return """
                {"goal":"%s","budgetVndSession":%d,"durationMinutes":90,"frequencyPer7Days":2,
                 "cycleStartDate":"%s"}""".formatted(GOAL, budget, start);
    }

    /**
     * Sends the request twice with one key; both answer {@code expected} with the same X-Operation-Id, and
     * getCommandStatus by it answers {@code commandType} for {@code resultId} (read from the first answer's id
     * when null). Returns the first answer.
     */
    private ResultActions twice(Actor actor, MockHttpServletRequestBuilder request, int expected, String commandType)
            throws Exception {
        String key = UUID.randomUUID().toString();
        request.with(sessions().as(actor)).header("Idempotency-Key", key);
        ResultActions first = mvc.perform(request).andExpect(status().is(expected)).andExpect(Contract.matches())
                .andExpect(header().exists(HEADER));
        String operation = first.andReturn().getResponse().getHeader(HEADER);
        mvc.perform(request).andExpect(status().is(expected)).andExpect(Contract.matches())
                .andExpect(header().string(HEADER, operation));

        mvc.perform(get("/api/v1/operations/" + operation).with(sessions().as(actor)))
                .andExpect(status().isOk()).andExpect(Contract.matches())
                .andExpect(jsonPath("$.operationId").value(operation))
                .andExpect(jsonPath("$.commandType").value(commandType))
                .andExpect(jsonPath("$.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultId").value(id(first).toString()));
        return first;
    }

    @Test
    void TX16_create_update_and_confirm_of_a_need_send_the_receipt_id_and_a_replay_sends_it_again()
            throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(student, parent.accountId()).update();
        LocalDate start = LocalDate.now(OfferHttpFixture.VIETNAM).plusDays(3);
        fx.calendar("STUDENT", student, start);

        UUID need = id(twice(parent, post("/api/v1/students/" + student + "/needs")
                .contentType(MediaType.APPLICATION_JSON).content(draft(start, 200_000)), 201, "CREATE_NEED_DRAFT"));
        twice(parent, patch("/api/v1/needs/" + need).header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content(draft(start, 210_000)), 200, "UPDATE_NEED_DRAFT");
        twice(parent, post("/api/v1/needs/" + need + "/confirm").header("If-Match", "\"2\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"), 200, "CONFIRM_NEED");
    }

    @Test
    void TX16_matching_send_reject_and_cancel_send_the_receipt_id_and_a_replay_sends_it_again() throws Exception {
        Scenario s = fx.scenario();
        Actor tutor = s.candidate().tutor().actor();

        twice(s.parent(), post("/api/v1/needs/" + s.need().id() + "/matching-runs").header("If-Match", "\"2\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"B\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":true}"
                        .formatted(s.need().versionId())), 201, "CREATE_MATCHING_RUN");

        MockHttpServletRequestBuilder send = post("/api/v1/needs/" + s.need().id() + "/offers").header("If-Match", "\"2\"")
                .contentType(MediaType.APPLICATION_JSON).content(input(s.need(), s.candidate()));
        UUID rejected = id(twice(s.parent(), send, 201, "SEND_OFFER"));
        twice(tutor, post("/api/v1/offers/" + rejected + "/reject").header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content(reason("NOT_AVAILABLE")), 200, "REJECT_OFFER");

        UUID cancelled = id(fx.send(s).andExpect(status().isCreated()).andExpect(header().exists(HEADER)));
        twice(s.parent(), post("/api/v1/offers/" + cancelled + "/cancel").header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content(reason("PARENT_CANCELLED")), 200, "CANCEL_OFFER");

        assertThat(fx.count("select count(*) from class_offers where need_id = ?", s.need().id())).isEqualTo(2);
    }
}
