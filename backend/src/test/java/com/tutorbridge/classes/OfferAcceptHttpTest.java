package com.tutorbridge.classes;

import static com.tutorbridge.classes.OfferHttpFixture.body;
import static com.tutorbridge.classes.OfferHttpFixture.id;
import static com.tutorbridge.classes.OfferHttpFixture.input;
import static com.tutorbridge.classes.OfferHttpFixture.reason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Concurrently;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.classes.OfferHttpFixture.Candidate;
import com.tutorbridge.classes.OfferHttpFixture.Need;
import com.tutorbridge.classes.OfferHttpFixture.Scenario;
import com.tutorbridge.classes.OfferHttpFixture.Tutor;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * acceptOffer over HTTP (UC06, TX05, TB-19). Real PostgreSQL, every answer checked against the contract.
 * The two-instance cases of TX05 stay in {@code AcceptOfferConcurrencyTest}; here the HTTP layer, the access
 * checks and the lock waits. Fake accounts and texts only.
 */
class OfferAcceptHttpTest extends HttpTest {

    private static final String ACCEPT = "{\"acceptUnchangedTerms\":true}";

    @Autowired
    TransactionTemplate tx;

    private OfferHttpFixture fx;

    @BeforeEach
    void fixture() {
        fx = new OfferHttpFixture(mvc, jdbc, tx, a -> sessions().as(a), this::newActor);
    }

    private ResultActions accept(Actor actor, UUID offerId, String key, String ifMatch, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/offers/" + offerId + "/accept")
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (actor != null) {
            request.with(sessions().as(actor));
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions accept(Actor tutor, UUID offerId) throws Exception {
        return accept(tutor, offerId, UUID.randomUUID().toString(), "\"1\"", ACCEPT);
    }

    private static Actor tutorOf(Scenario s) {
        return s.candidate().tutor().actor();
    }

    private UUID sent(Scenario s) throws Exception {
        return id(fx.send(s).andExpect(status().isCreated()));
    }

    private int classesOf(Need need) {
        return fx.count("select count(*) from classes where originating_need_id = ?", need.id());
    }

    private String offerState(UUID offer) {
        return jdbc.sql("select state || '/' || coalesce(reason_code, '-') || '/' || revision from class_offers where id = ?")
                .param(offer).query(String.class).single();
    }

    private int acceptReceipts(Actor tutor) {
        return fx.count("select count(*) from command_receipts where actor_id = ? and command_type = 'ACCEPT_OFFER'",
                tutor.accountId());
    }

    /** Nothing of TX05 was written: no class, no orphan session, the offer and the need as they were. */
    private void assertNothingCreated(Scenario s, UUID offer) {
        assertThat(classesOf(s.need())).isZero();
        assertThat(fx.count("""
                select count(*) from lesson_sessions s
                where not exists (select 1 from classes c where c.id = s.class_id)
                """)).isZero();
        assertThat(offerState(offer)).isEqualTo("PENDING/-/1");
        assertThat(fx.count("select count(*) from learning_needs where id = ? and status = 'CONFIRMED'", s.need().id()))
                .isOne();
    }

    /** A second need of a new parent on the same days, found with the same tutor, and its offer. */
    private UUID offerOfAnotherFamily(Tutor tutor, Scenario like) throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Need need = fx.need(parent, like.need().start());
        Candidate candidate = fx.run(parent, need, tutor);
        return id(fx.send(parent, need.id(), UUID.randomUUID().toString(), "\"2\"", input(need, candidate))
                .andExpect(status().isCreated()));
    }

    // ---------------------------------------------------------------- TX05: the recipient accepts

    @Test
    void TX05_the_recipient_accepts_and_gets_the_class_with_its_etag_and_receipt_id() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);

        String json = body(accept(tutorOf(s), offer)
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().exists("X-Operation-Id"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.studentId").value(s.need().studentId().toString()))
                .andExpect(jsonPath("$.tutorId").value(tutorOf(s).accountId().toString()))
                .andExpect(jsonPath("$.priceVndSession").value(OfferHttpFixture.PRICE))
                .andExpect(jsonPath("$.goal").value(OfferHttpFixture.GOAL))
                .andExpect(jsonPath("$.operationId").doesNotExist()));
        UUID classId = UUID.fromString(JsonPath.read(json, "$.id"));

        // INV-10: one class, its goal, every session and two bookings per session, in one commit.
        int sessions = s.candidate().schedule().size();
        assertThat(fx.count("select count(*) from lesson_sessions where class_id = ?", classId)).isEqualTo(sessions);
        assertThat(fx.count("""
                select count(*) from resource_bookings b join lesson_sessions s on s.id = b.session_id
                where s.class_id = ? and b.active
                """, classId)).isEqualTo(2 * sessions);
        assertThat(fx.count("select count(*) from class_goal_versions where class_id = ?", classId)).isOne();
        assertThat(offerState(offer)).isEqualTo("ACCEPTED/-/2");
        assertThat(fx.count("select count(*) from learning_needs where id = ? and status = 'CLOSED'", s.need().id()))
                .isOne();
        assertThat(fx.count("select count(*) from outbox_events where aggregate_id = ? and event_type = 'CLASS_CREATED'",
                classId)).isOne();
        // Both sides read the offer as accepted, with the class it made.
        fx.read(s.parent(), offer).andExpect(jsonPath("$.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.classId").value(classId.toString()));
        fx.read(tutorOf(s), offer).andExpect(jsonPath("$.classId").value(classId.toString()));
    }

    // ---------------------------------------------------------------- AC-UC06-01, INV-02: who may accept

    @Test
    void AC_UC06_01_only_the_recipient_tutor_accepts() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);

        // Another tutor learns nothing more than for a missing offer (INV-06).
        accept(newActor(Actor.Role.TUTOR), offer).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
        accept(tutorOf(s), UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
        // A parent, the one of the offer too, does not accept (only the tutor does, as only the parent cancels).
        accept(s.parent(), offer).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
        accept(newActor(Actor.Role.PARENT), offer).andExpect(status().isForbidden());
        accept(null, offer).andExpect(status().isUnauthorized());

        assertNothingCreated(s, offer);
        assertThat(acceptReceipts(tutorOf(s))).isZero();
    }

    @Test
    void AC_UC06_01_a_locked_tutor_cannot_accept() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(tutorOf(s).accountId()).update();

        accept(tutorOf(s), offer).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
        assertNothingCreated(s, offer);
    }

    @Test
    void AC_UC06_01_a_rejected_cancelled_or_overdue_offer_makes_no_class() throws Exception {
        Scenario rejected = fx.scenario();
        UUID rejectedOffer = sent(rejected);
        fx.respond(tutorOf(rejected), "reject", rejectedOffer, UUID.randomUUID().toString(), "\"1\"",
                reason("DECLINED")).andExpect(status().isOk());
        accept(tutorOf(rejected), rejectedOffer, UUID.randomUUID().toString(), "\"2\"", ACCEPT)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCOPE_CHANGED"));
        assertThat(classesOf(rejected.need())).isZero();

        Scenario cancelled = fx.scenario();
        UUID cancelledOffer = sent(cancelled);
        fx.respond(cancelled.parent(), "cancel", cancelledOffer, UUID.randomUUID().toString(), "\"1\"",
                reason("PARENT_CANCELLED")).andExpect(status().isOk());
        accept(tutorOf(cancelled), cancelledOffer, UUID.randomUUID().toString(), "\"2\"", ACCEPT)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCOPE_CHANGED"));
        assertThat(classesOf(cancelled.need())).isZero();

        // Past its deadline before the job wrote EXPIRED.
        Scenario late = fx.scenario();
        UUID overdue = fx.overdueOffer(late);
        accept(tutorOf(late), overdue).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_EXPIRED"));
        assertNothingCreated(late, overdue);
    }

    // ---------------------------------------------------------------- BR06: body, versions, keys

    @Test
    void BR06_terms_not_accepted_no_key_no_if_match_and_a_stale_if_match_change_nothing() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);

        accept(tutorOf(s), offer, UUID.randomUUID().toString(), "\"1\"", "{\"acceptUnchangedTerms\":false}")
                .andExpect(status().isUnprocessableContent());
        accept(tutorOf(s), offer, UUID.randomUUID().toString(), "\"1\"", "{}").andExpect(status().isUnprocessableContent());
        accept(tutorOf(s), offer, null, "\"1\"", ACCEPT).andExpect(status().isBadRequest());
        accept(tutorOf(s), offer, UUID.randomUUID().toString(), null, ACCEPT)
                .andExpect(status().isPreconditionRequired());
        accept(tutorOf(s), offer, UUID.randomUUID().toString(), "\"2\"", ACCEPT)
                .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        assertNothingCreated(s, offer);
    }

    @Test
    void AC_UC06_04_the_same_key_replays_the_class_and_another_if_match_with_it_is_409() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        String key = UUID.randomUUID().toString();

        ResultActions first = accept(tutorOf(s), offer, key, "\"1\"", ACCEPT).andExpect(status().isCreated());
        String operation = first.andReturn().getResponse().getHeader("X-Operation-Id");
        ResultActions again = accept(tutorOf(s), offer, key, "\"1\"", ACCEPT).andExpect(status().isCreated())
                .andExpect(header().string("X-Operation-Id", operation));

        assertThat(body(again)).isEqualTo(body(first));
        assertThat(classesOf(s.need())).isOne();
        assertThat(fx.count("select count(*) from outbox_events where event_type = 'CLASS_CREATED' and aggregate_id = ?",
                id(first))).isOne();
        accept(tutorOf(s), offer, key, "\"2\"", ACCEPT).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void AC_UC06_03_accepting_the_other_offer_of_a_closed_need_is_409_and_it_was_cancelled() throws Exception {
        Scenario s = fx.scenario();
        UUID first = sent(s);
        Tutor other = fx.tutor("Thầy Nam Thử", s.need().start());
        Candidate second = fx.run(s.parent(), s.need(), other);
        UUID competing = id(fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"2\"",
                input(s.need(), second)).andExpect(status().isCreated()));

        accept(tutorOf(s), first).andExpect(status().isCreated());

        assertThat(offerState(competing)).isEqualTo("CANCELLED/NEED_CLOSED/2");
        accept(other.actor(), competing, UUID.randomUUID().toString(), "\"2\"", ACCEPT)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCOPE_CHANGED"));
        assertThat(classesOf(s.need())).isOne();
    }

    // ---------------------------------------------------------------- AC-UC06-02, AC-UC06-03 at once

    /** Holds this lock from its own connection until every request is seen waiting on it, then releases it. */
    private List<Integer> whileHeld(String lockSql, Object param, String waitingOn, List<Request> requests)
            throws Exception {
        List<CompletableFuture<Integer>> answers = new ArrayList<>();
        try (Connection hold = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            hold.setAutoCommit(false);
            try (var lock = hold.prepareStatement(lockSql)) {
                lock.setObject(1, param);
                lock.executeQuery();
            }
            for (Request request : requests) {
                answers.add(Concurrently.supply(() -> statusOf(request)));
            }
            // They really overlap: each waits on the held lock.
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> fx.count("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like ? and query not like '%pg_stat_activity%'
                    """, waitingOn) == requests.size());
            hold.commit();
        }
        List<Integer> statuses = new ArrayList<>();
        for (CompletableFuture<Integer> answer : answers) {
            statuses.add(answer.get(10, TimeUnit.SECONDS));
        }
        return statuses;
    }

    @Test
    void AC_UC06_02_two_families_accepted_at_once_for_the_same_tutor_hours_make_one_class() throws Exception {
        Scenario s = fx.scenario();
        UUID first = sent(s);
        UUID second = offerOfAnotherFamily(s.candidate().tutor(), s);

        List<Integer> statuses = whileHeld("select pg_advisory_xact_lock(hashtextextended(?, 0))",
                "TUTOR:" + tutorOf(s).accountId(), "select pg_advisory_xact_lock%",
                List.of(() -> accept(tutorOf(s), first), () -> accept(tutorOf(s), second)));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(fx.count("select count(*) from classes where tutor_id = ?", tutorOf(s).accountId())).isOne();
        assertThat(fx.count("""
                select count(*) from lesson_sessions s
                where not exists (select 1 from classes c where c.id = s.class_id)
                """)).isZero();
        assertThat(fx.count("select count(*) from class_offers where id in (?, ?) and state = 'PENDING'", first, second))
                .isOne();
    }

    @Test
    void AC_UC06_03_two_offers_of_one_need_accepted_at_once_make_one_class() throws Exception {
        Scenario s = fx.scenario();
        UUID first = sent(s);
        Tutor other = fx.tutor("Thầy Nam Thử", s.need().start());
        Candidate second = fx.run(s.parent(), s.need(), other);
        UUID competing = id(fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"2\"",
                input(s.need(), second)).andExpect(status().isCreated()));

        List<Integer> statuses = whileHeld("select 1 from tutorbridge.learning_needs where id = ? for update",
                s.need().id(), "select student_id, status, current_version_id from learning_needs%",
                List.of(() -> accept(tutorOf(s), first), () -> accept(other.actor(), competing)));

        // The second sees the need closed and its offer cancelled.
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(classesOf(s.need())).isOne();
        assertThat(fx.count("""
                select count(*) from class_offers
                where id in (?, ?) and (state = 'ACCEPTED' or (state = 'CANCELLED' and reason_code = 'NEED_CLOSED'))
                """, first, competing)).isEqualTo(2);
    }

    // ---------------------------------------------------------------- INV-01, INV-22, ADR-0007: lock waits

    @Test
    void INV_01_a_tutor_locked_while_the_accept_waits_on_the_offer_changes_nothing() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        CompletableFuture<Integer> answer;
        try (Connection hold = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            hold.setAutoCommit(false);
            try (var lock = hold.prepareStatement("select 1 from tutorbridge.class_offers where id = ? for update")) {
                lock.setObject(1, offer);
                lock.executeQuery();
            }
            answer = Concurrently.supply(() -> statusOf(() -> accept(tutorOf(s), offer)));
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> fx.count("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like '%from class_offers where id = $1 for update%'
                      and query not like '%pg_stat_activity%'
                    """) == 1);
            try (var lockAccount = hold.prepareStatement("update tutorbridge.accounts set status = 'LOCKED' where id = ?")) {
                lockAccount.setObject(1, tutorOf(s).accountId());
                lockAccount.executeUpdate();
            }
            hold.commit();
        }

        assertThat(answer.get(10, TimeUnit.SECONDS)).isEqualTo(403);
        assertNothingCreated(s, offer);
        assertThat(acceptReceipts(tutorOf(s))).isZero();
    }

    @Test
    void INV_22_a_tutor_locked_while_the_replay_waits_for_its_receipt_reads_nothing() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        String key = UUID.randomUUID().toString();
        accept(tutorOf(s), offer, key, "\"1\"", ACCEPT).andExpect(status().isCreated());
        CompletableFuture<Integer> replay;
        try (Connection hold = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            hold.setAutoCommit(false);
            try (var lock = hold.prepareStatement("""
                    select 1 from tutorbridge.command_receipts
                    where actor_id = ? and command_type = 'ACCEPT_OFFER' and idempotency_key = ? for update""")) {
                lock.setObject(1, tutorOf(s).accountId());
                lock.setString(2, key);
                lock.executeQuery();
            }
            replay = Concurrently.supply(() -> statusOf(() -> accept(tutorOf(s), offer, key, "\"1\"", ACCEPT)));
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

        assertThat(replay.get(10, TimeUnit.SECONDS)).isEqualTo(403);
    }

    @Test
    void ADR_0007_a_need_held_past_the_lock_timeout_is_503_and_writes_nothing() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = sent(s);
        try (Connection hold = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            hold.setAutoCommit(false);
            // As a sendOffer or an updateNeedDraft holding the need would.
            try (var lock = hold.prepareStatement("select 1 from tutorbridge.learning_needs where id = ? for update")) {
                lock.setObject(1, s.need().id());
                lock.executeQuery();
            }
            long started = System.nanoTime();
            accept(tutorOf(s), offer).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_BUSY"));
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(9));
            hold.rollback();
        }

        assertNothingCreated(s, offer);
        assertThat(acceptReceipts(tutorOf(s))).isZero();
    }

    @FunctionalInterface
    private interface Request {
        ResultActions perform() throws Exception;
    }

    private static int statusOf(Request request) {
        try {
            return request.perform().andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
