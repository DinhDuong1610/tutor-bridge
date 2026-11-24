package com.tutorbridge.classes;

import static com.tutorbridge.classes.OfferHttpFixture.body;
import static com.tutorbridge.classes.OfferHttpFixture.id;
import static com.tutorbridge.classes.OfferHttpFixture.reason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Concurrently;
import com.tutorbridge.HttpTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.classes.OfferHttpFixture.Scenario;
import com.tutorbridge.platform.api.Actor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * rejectOffer and cancelOffer over HTTP (UC06, TX06, TB-18) with Đính's answers on #18 of 25/11/2026 (Q4,
 * Q5). Real PostgreSQL, every answer checked against the contract. Fake accounts and texts only.
 */
class OfferRespondHttpTest extends HttpTest {

    @Autowired
    TransactionTemplate tx;

    private OfferHttpFixture fx;

    @BeforeEach
    void fixture() {
        fx = new OfferHttpFixture(mvc, jdbc, tx, a -> sessions().as(a), this::newActor);
    }

    private Actor tutorOf(Scenario s) {
        return s.candidate().tutor().actor();
    }

    private String stateOf(UUID offer) {
        return jdbc.sql("select state || '/' || coalesce(reason_code, '-') || '/' || revision from class_offers where id = ?")
                .param(offer).query(String.class).single();
    }

    private int events(String type, UUID offer) {
        return fx.count("select count(*) from outbox_events where event_type = ? and aggregate_id = ?", type, offer);
    }

    private int classesOf(Scenario s) {
        return fx.count("select count(*) from classes where originating_need_id = ?", s.need().id());
    }

    private UUID sent(Scenario s) throws Exception {
        return id(fx.send(s).andExpect(status().isCreated()));
    }

    // ---------------------------------------------------------------- TX06: the tutor rejects, the parent cancels

    @Test
    void AC_UC06_01_the_tutor_rejects_with_a_reason_and_no_class_is_made() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);

        fx.respond(tutorOf(s), "reject", offer, UUID.randomUUID().toString(), "\"1\"", reason("NOT_AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.state").value("REJECTED"))
                .andExpect(jsonPath("$.reasonCode").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.classId").isEmpty());

        assertThat(stateOf(offer)).isEqualTo("REJECTED/NOT_AVAILABLE/2");
        assertThat(events("OFFER_REJECTED", offer)).isOne();
        assertThat(classesOf(s)).isZero();
        fx.read(s.parent(), offer).andExpect(jsonPath("$.state").value("REJECTED"));
    }

    @Test
    void AC_UC06_01_the_parent_cancels_a_pending_offer_and_may_then_send_to_the_tutor_again() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);

        fx.respond(s.parent(), "cancel", offer, UUID.randomUUID().toString(), "\"1\"", reason("PARENT_CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CANCELLED"))
                .andExpect(jsonPath("$.reasonCode").value("PARENT_CANCELLED"));

        assertThat(events("OFFER_CANCELLED", offer)).isOne();
        assertThat(classesOf(s)).isZero();
        // The pair has no pending offer any more (one_pending_offer_per_pair).
        assertThat(id(fx.send(s).andExpect(status().isCreated()))).isNotEqualTo(offer);
    }

    // ---------------------------------------------------------------- AC-UC06-01, INV-02: who may answer

    @Test
    void AC_UC06_01_another_tutor_another_parent_the_wrong_role_and_no_session_change_nothing() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);

        fx.respond(newActor(Actor.Role.TUTOR), "reject", offer, UUID.randomUUID().toString(), "\"1\"",
                reason("DECLINED")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
        fx.respond(newActor(Actor.Role.PARENT), "cancel", offer, UUID.randomUUID().toString(), "\"1\"",
                reason("OTHER")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
        // The parent does not reject and the tutor does not cancel.
        fx.respond(s.parent(), "reject", offer, UUID.randomUUID().toString(), "\"1\"", reason("DECLINED"))
                .andExpect(status().isForbidden());
        fx.respond(tutorOf(s), "cancel", offer, UUID.randomUUID().toString(), "\"1\"", reason("OTHER"))
                .andExpect(status().isForbidden());
        fx.respond(null, "reject", offer, UUID.randomUUID().toString(), "\"1\"", reason("DECLINED"))
                .andExpect(status().isUnauthorized());
        fx.respond(tutorOf(s), "reject", UUID.randomUUID(), UUID.randomUUID().toString(), "\"1\"", reason("DECLINED"))
                .andExpect(status().isNotFound());

        assertThat(stateOf(offer)).isEqualTo("PENDING/-/1");
    }

    // ---------------------------------------------------------------- Q4, Q5: reasons

    @Test
    void Q5_a_reason_code_of_the_other_role_and_Q4_a_reason_text_are_422() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);

        fx.respond(tutorOf(s), "reject", offer, UUID.randomUUID().toString(), "\"1\"", reason("PARENT_CANCELLED"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.fields[0].path").value("reasonCode"));
        for (String code : List.of("NOT_AVAILABLE", "DECLINED")) {
            fx.respond(s.parent(), "cancel", offer, UUID.randomUUID().toString(), "\"1\"", reason(code))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.fields[0].path").value("reasonCode"));
        }
        fx.respond(tutorOf(s), "reject", offer, UUID.randomUUID().toString(), "\"1\"",
                        "{\"reasonCode\":\"OTHER\",\"reason\":\"Bận việc riêng.\"}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.fields[0].path").value("reason"));
        fx.respond(tutorOf(s), "reject", offer, UUID.randomUUID().toString(), "\"1\"", reason("SOMETHING"))
                .andExpect(status().isUnprocessableContent());

        assertThat(stateOf(offer)).isEqualTo("PENDING/-/1");
        assertThat(fx.count("select count(*) from command_receipts where actor_id in (?, ?) and command_type like '%_OFFER'"
                + " and command_type <> 'SEND_OFFER'", tutorOf(s).accountId(), s.parent().accountId())).isZero();
    }

    // ---------------------------------------------------------------- BR06: versions, keys, final states

    @Test
    void INV_23_a_stale_if_match_is_412_none_is_428_and_no_key_is_400() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);

        fx.respond(tutorOf(s), "reject", offer, UUID.randomUUID().toString(), "\"2\"", reason("DECLINED"))
                .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        fx.respond(tutorOf(s), "reject", offer, UUID.randomUUID().toString(), null, reason("DECLINED"))
                .andExpect(status().isPreconditionRequired());
        fx.respond(tutorOf(s), "reject", offer, null, "\"1\"", reason("DECLINED")).andExpect(status().isBadRequest());

        assertThat(stateOf(offer)).isEqualTo("PENDING/-/1");
    }

    @Test
    void BR06_the_same_key_replays_the_answer_and_another_reason_with_it_is_409() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        String key = UUID.randomUUID().toString();

        String first = body(fx.respond(tutorOf(s), "reject", offer, key, "\"1\"", reason("DECLINED"))
                .andExpect(status().isOk()));
        String again = body(fx.respond(tutorOf(s), "reject", offer, key, "\"1\"", reason("DECLINED"))
                .andExpect(status().isOk()));

        assertThat(again).isEqualTo(first);
        assertThat(events("OFFER_REJECTED", offer)).isOne();
        fx.respond(tutorOf(s), "reject", offer, key, "\"1\"", reason("OTHER"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void TX06_an_offer_that_already_ended_stays_as_it_is() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        fx.respond(s.parent(), "cancel", offer, UUID.randomUUID().toString(), "\"1\"", reason("OTHER"))
                .andExpect(status().isOk());

        // With the offer's current ETag: the answer is that it ended, not a silent reversal.
        fx.respond(tutorOf(s), "reject", offer, UUID.randomUUID().toString(), "\"2\"", reason("DECLINED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_NOT_PENDING"));

        assertThat(stateOf(offer)).isEqualTo("CANCELLED/OTHER/2");
        assertThat(events("OFFER_REJECTED", offer)).isZero();
    }

    @Test
    void Q1_an_overdue_offer_is_409_offer_expired_and_left_for_the_job() throws Exception {
        Scenario s = fx.scenario();
        UUID overdue = fx.overdueOffer(s);

        fx.respond(tutorOf(s), "reject", overdue, UUID.randomUUID().toString(), "\"1\"", reason("DECLINED"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_EXPIRED"));
        fx.respond(s.parent(), "cancel", overdue, UUID.randomUUID().toString(), "\"1\"", reason("OTHER"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_EXPIRED"));

        assertThat(stateOf(overdue)).isEqualTo("PENDING/-/1");
    }

    // ---------------------------------------------------------------- TX06: one final state

    @Test
    void TX06_a_reject_and_a_cancel_at_once_end_the_offer_once() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        List<CompletableFuture<Integer>> answers = new ArrayList<>();
        try (Connection hold = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            hold.setAutoCommit(false);
            try (var lock = hold.prepareStatement("select 1 from tutorbridge.class_offers where id = ? for update")) {
                lock.setObject(1, offer);
                lock.executeQuery();
            }
            answers.add(Concurrently.supply(() -> statusOf(() -> fx.respond(tutorOf(s), "reject", offer,
                    UUID.randomUUID().toString(), "\"1\"", reason("DECLINED")))));
            answers.add(Concurrently.supply(() -> statusOf(() -> fx.respond(s.parent(), "cancel", offer,
                    UUID.randomUUID().toString(), "\"1\"", reason("OTHER")))));
            // Both really wait on the offer row.
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> fx.count("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like 'select 1 from class_offers where id = $1 for update%'
                    """) == 2);
            hold.commit();
        }
        List<Integer> statuses = new ArrayList<>();
        for (CompletableFuture<Integer> answer : answers) {
            statuses.add(answer.get(10, TimeUnit.SECONDS));
        }

        // The second sees revision 2 and its If-Match "1" is stale.
        assertThat(statuses).containsExactlyInAnyOrder(200, 412);
        assertThat(stateOf(offer)).matches("(REJECTED/DECLINED|CANCELLED/OTHER)/2");
        assertThat(events("OFFER_REJECTED", offer) + events("OFFER_CANCELLED", offer)).isOne();
    }

    // ---------------------------------------------------------------- INV-01: access lost while waiting

    /**
     * Codex's case on #235: the answer waits on the offer row, the account is locked and that commits,
     * then the row is released. Nothing may change: no state, no event, no receipt.
     */
    private void lockedWhileWaiting(boolean asParent, String action, String code) throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        Actor who = asParent ? s.parent() : tutorOf(s);
        CompletableFuture<Integer> answer;
        try (Connection hold = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            hold.setAutoCommit(false);
            try (var lock = hold.prepareStatement("select 1 from tutorbridge.class_offers where id = ? for update")) {
                lock.setObject(1, offer);
                lock.executeQuery();
            }
            answer = Concurrently.supply(() -> statusOf(() -> fx.respond(who, action, offer,
                    UUID.randomUUID().toString(), "\"1\"", reason(code))));
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> fx.count("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like 'select 1 from class_offers where id = $1 for update%'
                    """) == 1);
            try (var lockAccount = hold.prepareStatement("update tutorbridge.accounts set status = 'LOCKED' where id = ?")) {
                lockAccount.setObject(1, who.accountId());
                lockAccount.executeUpdate();
            }
            hold.commit();
        }

        assertThat(answer.get(10, TimeUnit.SECONDS)).isEqualTo(404);
        assertThat(stateOf(offer)).isEqualTo("PENDING/-/1");
        assertThat(events("OFFER_CANCELLED", offer) + events("OFFER_REJECTED", offer)).isZero();
        assertThat(fx.count("select count(*) from command_receipts where actor_id = ? and command_type in "
                + "('REJECT_OFFER', 'CANCEL_OFFER')", who.accountId())).isZero();
    }

    @Test
    void INV_01_a_parent_locked_while_the_cancel_waits_changes_nothing() throws Exception {
        lockedWhileWaiting(true, "cancel", "OTHER");
    }

    @Test
    void INV_01_a_tutor_locked_while_the_reject_waits_changes_nothing() throws Exception {
        lockedWhileWaiting(false, "reject", "DECLINED");
    }

    @Test
    void INV_22_a_replay_by_a_parent_locked_since_the_cancel_is_404() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        String key = UUID.randomUUID().toString();
        String first = body(fx.respond(s.parent(), "cancel", offer, key, "\"1\"", reason("OTHER"))
                .andExpect(status().isOk()));
        // Answered again unchanged while the parent may still act.
        assertThat(body(fx.respond(s.parent(), "cancel", offer, key, "\"1\"", reason("OTHER"))
                .andExpect(status().isOk()))).isEqualTo(first);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(s.parent().accountId()).update();

        fx.respond(s.parent(), "cancel", offer, key, "\"1\"", reason("OTHER"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
    }

    @Test
    void INV_22_a_tutor_locked_while_the_replay_waits_for_its_receipt_reads_nothing() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        String key = UUID.randomUUID().toString();
        fx.respond(tutorOf(s), "reject", offer, key, "\"1\"", reason("DECLINED")).andExpect(status().isOk());
        CompletableFuture<Integer> replay;
        try (Connection hold = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            hold.setAutoCommit(false);
            // Codex's case on #235: the replay waits on its own receipt while the tutor is still ACTIVE.
            try (var lock = hold.prepareStatement("""
                    select 1 from tutorbridge.command_receipts
                    where actor_id = ? and command_type = 'REJECT_OFFER' and idempotency_key = ? for update""")) {
                lock.setObject(1, tutorOf(s).accountId());
                lock.setString(2, key);
                lock.executeQuery();
            }
            replay = Concurrently.supply(() -> statusOf(() -> fx.respond(tutorOf(s), "reject", offer, key, "\"1\"",
                    reason("DECLINED"))));
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> fx.count("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like '%from command_receipts%'
                      and query not like '%pg_stat_activity%'
                    """) == 1);
            try (var lockAccount = hold.prepareStatement("update tutorbridge.accounts set status = 'LOCKED' where id = ?")) {
                lockAccount.setObject(1, tutorOf(s).accountId());
                lockAccount.executeUpdate();
            }
            hold.commit();
        }

        assertThat(replay.get(10, TimeUnit.SECONDS)).isEqualTo(404);
        assertThat(stateOf(offer)).isEqualTo("REJECTED/DECLINED/2");
    }

    @Test
    void BR06_INV_20_a_send_replayed_after_the_cancel_still_answers_the_sent_offer() throws Exception {
        Scenario s = fx.scenario();
        String key = UUID.randomUUID().toString();
        String json = OfferHttpFixture.input(s.need(), s.candidate());
        String sentBody = body(fx.send(s.parent(), s.need().id(), key, "\"2\"", json).andExpect(status().isCreated()));
        UUID offer = UUID.fromString(com.jayway.jsonpath.JsonPath.read(sentBody, "$.id"));
        fx.respond(s.parent(), "cancel", offer, UUID.randomUUID().toString(), "\"1\"", reason("OTHER"))
                .andExpect(status().isOk());

        assertThat(body(fx.send(s.parent(), s.need().id(), key, "\"2\"", json).andExpect(status().isCreated())))
                .isEqualTo(sentBody);
    }

    @FunctionalInterface
    private interface Request {
        org.springframework.test.web.servlet.ResultActions perform() throws Exception;
    }

    private static int statusOf(Request request) {
        try {
            return request.perform().andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
