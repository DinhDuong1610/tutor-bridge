package com.tutorbridge.classes;

import static com.tutorbridge.classes.OfferHttpFixture.DESCRIPTION;
import static com.tutorbridge.classes.OfferHttpFixture.VIETNAM;
import static com.tutorbridge.classes.OfferHttpFixture.farCycle;
import static com.tutorbridge.classes.OfferHttpFixture.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.classes.OfferHttpFixture.Need;
import com.tutorbridge.platform.api.Actor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AT02 (UC05, TB-23, Đính's part): when the budget or the schedule lets no tutor through, the run is an
 * empty COMPLETED run with no failure code, so the app shows "Chưa có gia sư đáp ứng đủ điều kiện." with
 * the four groups of conditions it checked (PH06), never an error. Nothing is relaxed: the tutor just past
 * the budget or one sitting short of a session stays out, the need keeps its terms, and no sample profile
 * fills the list.
 *
 * <p>Each empty case has a control that differs in that one condition only and finds the tutor, so the
 * empty answer comes from the condition, not from a scenario no one could pass. The need is the fixture's:
 * 90 minutes, twice a week, 200.000 ₫ a session, the child free 18:00–22:00 every day of its own far cycle,
 * where only this test's tutor is free (`OfferHttpFixture`). Real PostgreSQL, every answer checked against
 * the contract. Fake accounts only.
 */
class At02EmptyMatchingHttpTest extends HttpTest {

    /** ceil(133.333 × 90 / 60) = 200.000 ₫, the budget; one more đồng an hour is 200.001 ₫ a session. */
    private static final int RATE_AT_BUDGET = 133_333;

    @Autowired
    TransactionTemplate tx;

    private OfferHttpFixture fx;

    @BeforeEach
    void fixture() {
        fx = new OfferHttpFixture(mvc, jdbc, tx, a -> sessions().as(a), this::newActor);
    }

    // ---------------------------------------------------------------- budget

    @Test
    void AT02_a_tutor_one_dong_over_the_budget_leaves_an_empty_completed_run_and_the_budget_as_it_was()
            throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = fx.need(parent, start);
        UUID tutor = tutor(RATE_AT_BUDGET + 1, start, LocalTime.of(18, 0), LocalTime.of(22, 0));

        assertEmpty(run(parent, need), need, tutor);
    }

    @Test
    void AT02_control_the_same_tutor_at_the_budget_is_found() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = fx.need(parent, start);
        UUID tutor = tutor(RATE_AT_BUDGET, start, LocalTime.of(18, 0), LocalTime.of(22, 0));

        String body = run(parent, need);
        List<String> found = JsonPath.read(body, "$.candidates[*].tutor.tutorId");
        assertThat(found).containsExactly(tutor.toString());
        assertThat((Integer) JsonPath.read(body, "$.candidates[0].priceVndSession")).isEqualTo(200_000);
    }

    // ---------------------------------------------------------------- schedule

    @Test
    void AT02_a_tutor_free_only_60_of_the_90_minutes_leaves_an_empty_completed_run() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = fx.need(parent, start);
        UUID tutor = tutor(RATE_AT_BUDGET, start, LocalTime.of(18, 0), LocalTime.of(19, 0));

        assertEmpty(run(parent, need), need, tutor);
    }

    @Test
    void AT02_control_the_same_tutor_free_the_90_minutes_is_found() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = fx.need(parent, start);
        UUID tutor = tutor(RATE_AT_BUDGET, start, LocalTime.of(18, 0), LocalTime.of(19, 30));

        List<String> found = JsonPath.read(run(parent, need), "$.candidates[*].tutor.tutorId");
        assertThat(found).containsExactly(tutor.toString());
    }

    // ---------------------------------------------------------------- checks

    /**
     * Empty and COMPLETED, not FAILED: an empty list and an error stay apart (AC-UC05-04). Nothing stored
     * as a candidate, the tutor left out, and the need still the version the parent confirmed.
     */
    private void assertEmpty(String body, Need need, UUID tutor) {
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("COMPLETED");
        Object failureCode = JsonPath.read(body, "$.failureCode");
        List<Object> candidates = JsonPath.read(body, "$.candidates");
        assertThat(failureCode).isNull();
        assertThat(candidates).isEmpty();
        assertThat(body).doesNotContain(tutor.toString());
        UUID runId = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(fx.count("select count(*) from matching_candidates where run_id = ?", runId)).isZero();
        // Not relaxed: still one version, with the budget, duration and frequency the parent confirmed.
        assertThat(jdbc.sql("""
                select concat_ws('/', count(*), min(budget_vnd_session), min(duration_minutes), min(frequency))
                from need_versions where need_id = ?""").param(need.id()).query(String.class).single())
                .isEqualTo("1/200000/90/2");
        assertThat(jdbc.sql("select status || '/' || revision from learning_needs where id = ?").param(need.id())
                .query(String.class).single()).isEqualTo("CONFIRMED/2");
    }

    /** createMatchingRun B over HTTP, answered 202 and checked against the contract. */
    private String run(Actor parent, Need need) throws Exception {
        return mvc.perform(post("/api/v1/needs/" + need.id() + "/matching-runs").with(sessions().as(parent))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).header("If-Match", "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"B\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":true}"
                                .formatted(need.versionId())))
                .andExpect(Contract.matches())
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(202))
                .andReturn().getResponse().getContentAsString();
    }

    /** An ACTIVE tutor with a current APPROVED profile at {@code rate}, free {@code from}–{@code to} each day. */
    private UUID tutor(int rate, LocalDate start, LocalTime from, LocalTime to) {
        Actor tutor = newActor(Actor.Role.TUTOR);
        jdbc.sql("update accounts set display_name = 'Cô Lan Thử' where id = ?").param(tutor.accountId()).update();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current)
                values (?, ?, 1, 'APPROVED', ?, 24, ?, true)
                """).params(UUID.randomUUID(), tutor.accountId(), DESCRIPTION, rate).update();
        UUID calendar = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, 'TUTOR', ?, 1, true)")
                .params(calendar, tutor.accountId()).update();
        IntStream.range(0, 28).forEach(i -> jdbc.sql("""
                insert into availability_windows(id, calendar_id, during, kind) values (?, ?, tstzrange(?, ?), 'AVAILABLE')
                """).params(UUID.randomUUID(), calendar, utc(start.plusDays(i).atTime(from).atZone(VIETNAM)),
                        utc(start.plusDays(i).atTime(to).atZone(VIETNAM))).update());
        return tutor.accountId();
    }
}
