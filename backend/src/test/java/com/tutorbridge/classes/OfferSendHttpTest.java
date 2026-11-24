package com.tutorbridge.classes;

import static com.tutorbridge.classes.OfferHttpFixture.body;
import static com.tutorbridge.classes.OfferHttpFixture.id;
import static com.tutorbridge.classes.OfferHttpFixture.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Concurrently;
import com.tutorbridge.HttpTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.classes.OfferHttpFixture.Candidate;
import com.tutorbridge.classes.OfferHttpFixture.Scenario;
import com.tutorbridge.classes.OfferHttpFixture.Tutor;
import com.tutorbridge.platform.api.Actor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * sendOffer, getOffer and listOffers over HTTP (UC06, TX04, TX06, TB-18) with Đính's answers on #18 of
 * 25/11/2026. Real PostgreSQL, every answer checked against the contract. Fake accounts and texts only.
 */
class OfferSendHttpTest extends HttpTest {

    @Autowired
    TransactionTemplate tx;

    private OfferHttpFixture fx;

    @BeforeEach
    void fixture() {
        fx = new OfferHttpFixture(mvc, jdbc, tx, a -> sessions().as(a), this::newActor);
    }

    private int offersOf(Scenario s) {
        return fx.count("select count(*) from class_offers where need_id = ?", s.need().id());
    }

    private int events(String type, UUID offerId) {
        return fx.count("select count(*) from outbox_events where event_type = ? and aggregate_id = ?", type, offerId);
    }

    // ---------------------------------------------------------------- TX04: send

    @Test
    void TX04_BR05_a_candidate_of_the_run_is_sent_with_fixed_terms_and_reserves_nothing() throws Exception {
        Scenario s = fx.scenario();
        int bookings = fx.count("select count(*) from resource_bookings");

        var sent = fx.send(s).andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.needId").value(s.need().id().toString()))
                .andExpect(jsonPath("$.needVersionId").value(s.need().versionId().toString()))
                .andExpect(jsonPath("$.tutorId").value(s.candidate().tutor().id().toString()))
                .andExpect(jsonPath("$.goalSnapshot").value(OfferHttpFixture.GOAL))
                .andExpect(jsonPath("$.durationMinutes").value(90))
                .andExpect(jsonPath("$.frequencyPer7Days").value(2))
                .andExpect(jsonPath("$.cycleStartDate").value(s.need().start().toString()))
                .andExpect(jsonPath("$.rateVnd60").value(OfferHttpFixture.RATE))
                .andExpect(jsonPath("$.priceVndSession").value(OfferHttpFixture.PRICE))
                .andExpect(jsonPath("$.reasonCode").isEmpty())
                .andExpect(jsonPath("$.classId").isEmpty());
        String json = body(sent);
        UUID offer = id(sent);

        // The sessions are the run's common schedule, in order (Q2).
        List<Map<String, String>> schedule = JsonPath.read(json, "$.schedule");
        assertThat(schedule).isEqualTo(s.candidate().schedule());
        // BR05: 48 hours to answer, as the first session is weeks away.
        Instant sentAt = Instant.parse(JsonPath.read(json, "$.sentAt"));
        assertThat(Instant.parse(JsonPath.<String>read(json, "$.expiresAt"))).isEqualTo(sentAt.plus(Duration.ofHours(48)));
        // INV-13: a pending offer books nothing. The event for TB-21 carries ids only (INV-63).
        assertThat(fx.count("select count(*) from resource_bookings")).isEqualTo(bookings);
        assertThat(events("OFFER_SENT", offer)).isOne();
        assertThat(jdbc.sql("select payload::text from outbox_events where event_type = 'OFFER_SENT' and aggregate_id = ?")
                .param(offer).query(String.class).single()).isEqualTo("{\"offerId\": \"" + offer + "\"}");
    }

    @Test
    void BR05_the_deadline_is_the_first_session_when_it_comes_before_48_hours() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        // The cycle starts today: the first free evening is at most a day away.
        LocalDate today = LocalDate.now(OfferHttpFixture.VIETNAM);
        var need = fx.need(parent, today);
        Tutor tutor = fx.tutor("Cô Lan Thử", today);
        // Other tests' tutors are free this month too: the most experienced ranks first among them.
        jdbc.sql("update tutor_profiles set experience_months = 600 where id = ?").param(tutor.profileId()).update();
        Candidate candidate = fx.run(parent, need, tutor);
        Instant first = Instant.parse(candidate.schedule().getFirst().get("start"));
        if (!first.isBefore(Instant.now().plus(Duration.ofHours(47)))) {
            throw new IllegalStateException("The first session should be within 48 hours: " + first);
        }

        var sent = fx.send(parent, need.id(), UUID.randomUUID().toString(), "\"2\"", input(need, candidate))
                .andExpect(status().isCreated());

        assertThat(Instant.parse(JsonPath.<String>read(body(sent), "$.expiresAt"))).isEqualTo(first);
    }

    @Test
    void BR06_the_same_key_and_body_answer_the_same_offer_and_another_body_is_409() throws Exception {
        Scenario s = fx.scenario();
        String key = UUID.randomUUID().toString();
        String json = input(s.need(), s.candidate());

        UUID first = id(fx.send(s.parent(), s.need().id(), key, "\"2\"", json).andExpect(status().isCreated()));
        UUID again = id(fx.send(s.parent(), s.need().id(), key, "\"2\"", json).andExpect(status().isCreated()));

        assertThat(again).isEqualTo(first);
        assertThat(offersOf(s)).isOne();
        assertThat(events("OFFER_SENT", first)).isOne();
        fx.send(s.parent(), s.need().id(), key, "\"3\"", json)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void BR06_INV_20_a_replay_after_the_offer_changed_answers_what_the_send_answered() throws Exception {
        Scenario s = fx.scenario();
        String key = UUID.randomUUID().toString();
        String json = input(s.need(), s.candidate());
        String first = body(fx.send(s.parent(), s.need().id(), key, "\"2\"", json).andExpect(status().isCreated()));
        UUID offer = UUID.fromString(JsonPath.read(first, "$.id"));
        // What a cancel does to the row (TX06, PR 2); the job and an accept change it the same way.
        jdbc.sql("""
                update class_offers set state = 'CANCELLED', revision = 2, concluded_at = now(),
                       reason_code = 'PARENT_CANCELLED' where id = ?""").param(offer).update();

        String again = body(fx.send(s.parent(), s.need().id(), key, "\"2\"", json)
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"1\"")));

        assertThat(again).isEqualTo(first);
        // The replay does not touch the offer, which reads as it is now.
        fx.read(s.parent(), offer).andExpect(jsonPath("$.state").value("CANCELLED"))
                .andExpect(jsonPath("$.revision").value(2));
        assertThat(offersOf(s)).isOne();
    }

    @Test
    void INV_22_a_replay_by_a_parent_locked_since_the_send_is_404() throws Exception {
        Scenario s = fx.scenario();
        String key = UUID.randomUUID().toString();
        String json = input(s.need(), s.candidate());
        fx.send(s.parent(), s.need().id(), key, "\"2\"", json).andExpect(status().isCreated());
        // Only the account is locked; the session the test signs with stays valid.
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(s.parent().accountId()).update();

        fx.send(s.parent(), s.need().id(), key, "\"2\"", json)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NEED_NOT_FOUND"));
    }

    /** The parent is checked after the wait for the need, so a lock committed meanwhile is seen. */
    @Test
    void INV_01_a_parent_locked_while_the_send_waits_for_the_need_sends_nothing() throws Exception {
        Scenario s = fx.scenario();
        CompletableFuture<Integer> sent;
        try (Connection edit = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            edit.setAutoCommit(false);
            try (var hold = edit.prepareStatement("select 1 from tutorbridge.learning_needs where id = ? for update")) {
                hold.setObject(1, s.need().id());
                hold.executeQuery();
            }
            sent = Concurrently.supply(() -> {
                try {
                    return fx.send(s).andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> fx.count("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like 'select 1 from learning_needs where id = $1 for update%'
                    """) == 1);
            // The parent's account is locked, committed while the send waits.
            try (var lock = edit.prepareStatement("update tutorbridge.accounts set status = 'LOCKED' where id = ?")) {
                lock.setObject(1, s.parent().accountId());
                lock.executeUpdate();
            }
            edit.commit();
        }

        assertThat(sent.get(10, TimeUnit.SECONDS)).isEqualTo(404);
        assertThat(offersOf(s)).isZero();
        assertThat(fx.count("select count(*) from command_receipts where actor_id = ? and command_type = 'SEND_OFFER'",
                s.parent().accountId())).isZero();
    }

    @Test
    void INV_01_locking_the_parent_waits_for_a_send_that_already_checked_them() throws Exception {
        Scenario s = fx.scenario();
        // An overdue pending offer of the pair, held by another transaction: the send checks the parent, then
        // waits on that row before it writes (TX04, lockPending).
        UUID overdue = fx.overdueOffer(s);
        CompletableFuture<Integer> sent;
        CompletableFuture<Void> locked;
        try (Connection hold = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            hold.setAutoCommit(false);
            try (var row = hold.prepareStatement("select 1 from tutorbridge.class_offers where id = ? for update")) {
                row.setObject(1, overdue);
                row.executeQuery();
            }
            sent = Concurrently.supply(() -> {
                try {
                    return fx.send(s).andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> fx.count("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like 'select id, expires_at from class_offers%'
                    """) == 1);
            // Locking the parent now waits on the send's FOR SHARE instead of slipping in under it.
            locked = Concurrently.run(() -> jdbc.sql("update accounts set status = 'LOCKED' where id = ?")
                    .param(s.parent().accountId()).update());
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> fx.count("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like 'update accounts set status%'
                    """) == 1);
            assertThat(locked).isNotDone();
            hold.commit();
        }

        // The send was checked first, so it goes through; the lock comes after it.
        assertThat(sent.get(10, TimeUnit.SECONDS)).isEqualTo(201);
        locked.get(10, TimeUnit.SECONDS);
        assertThat(offersOf(s)).isEqualTo(2);
    }

    @Test
    void AC_UC06_04_a_second_send_to_the_same_tutor_with_another_key_is_409_and_adds_nothing() throws Exception {
        Scenario s = fx.scenario();
        fx.send(s).andExpect(status().isCreated());

        fx.send(s).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_ALREADY_PENDING"));

        assertThat(offersOf(s)).isOne();
    }

    @Test
    void AC_UC06_04_two_sends_at_once_with_different_keys_make_one_offer() throws Exception {
        Scenario s = fx.scenario();
        List<CompletableFuture<Integer>> sends = new ArrayList<>();
        try (Connection edit = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            edit.setAutoCommit(false);
            // Holds the need as an edit would, so both sends queue on it and then run one after the other.
            try (var hold = edit.prepareStatement("select 1 from tutorbridge.learning_needs where id = ? for update")) {
                hold.setObject(1, s.need().id());
                hold.executeQuery();
            }
            for (int i = 0; i < 2; i++) {
                sends.add(Concurrently.supply(() -> {
                    try {
                        return fx.send(s).andReturn().getResponse().getStatus();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }));
            }
            // Both really wait on the need row.
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> fx.count("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and query like 'select 1 from learning_needs where id = $1 for update%'
                    """) == 2);
            edit.commit();
        }
        List<Integer> statuses = new ArrayList<>();
        for (CompletableFuture<Integer> send : sends) {
            statuses.add(send.get(10, TimeUnit.SECONDS));
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(offersOf(s)).isOne();
    }

    @Test
    void Q1_an_overdue_pending_offer_is_expired_by_the_next_send_instead_of_blocking_it() throws Exception {
        Scenario s = fx.scenario();
        UUID overdue = fx.overdueOffer(s);

        UUID sent = id(fx.send(s).andExpect(status().isCreated()));

        assertThat(sent).isNotEqualTo(overdue);
        assertThat(jdbc.sql("select state || '/' || reason_code || '/' || revision from class_offers where id = ?")
                .param(overdue).query(String.class).single()).isEqualTo("EXPIRED/OFFER_EXPIRED/2");
        assertThat(events("OFFER_EXPIRED", overdue)).isOne();
    }

    // ---------------------------------------------------------------- TX04: what is checked again

    @Test
    void BR06_INV_23_a_stale_if_match_is_412_and_none_is_428_and_nothing_is_written() throws Exception {
        Scenario s = fx.scenario();
        String json = input(s.need(), s.candidate());

        fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"1\"", json)
                .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), null, json)
                .andExpect(status().isPreconditionRequired());
        fx.send(s.parent(), s.need().id(), null, "\"2\"", json).andExpect(status().isBadRequest());

        assertThat(offersOf(s)).isZero();
        assertThat(fx.count("select count(*) from command_receipts where actor_id = ? and command_type = 'SEND_OFFER'",
                s.parent().accountId())).isZero();
    }

    @Test
    void UC06_an_offer_from_an_old_need_version_is_409_need_version_changed() throws Exception {
        Scenario s = fx.scenario();
        UUID other = UUID.randomUUID();

        fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"2\"",
                        input(other, s.candidate().runId(), s.candidate().tutor().id(), s.candidate().tutor().profileId(),
                                s.candidate().tutor().calendarId(), s.need().calendarId(),
                                OfferHttpFixture.schedule(s.candidate().schedule())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NEED_VERSION_CHANGED"));

        // The need was edited and confirmed again after the run: the run is of the old version.
        tx.executeWithoutResult(st -> {
            UUID next = UUID.randomUUID();
            jdbc.sql("""
                    insert into need_versions(id, need_id, version, goal, budget_vnd_session, duration_minutes,
                                              frequency, start_date, confirmed_at, student_calendar_id)
                    select ?, need_id, 2, goal, budget_vnd_session, duration_minutes, frequency, start_date, now(),
                           student_calendar_id
                    from need_versions where id = ?""").params(next, s.need().versionId()).update();
            jdbc.sql("update learning_needs set current_version_id = ?, revision = 4 where id = ?")
                    .params(next, s.need().id()).update();
        });
        UUID current = jdbc.sql("select current_version_id from learning_needs where id = ?").param(s.need().id())
                .query(UUID.class).single();
        fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"4\"",
                        input(current, s.candidate().runId(), s.candidate().tutor().id(),
                                s.candidate().tutor().profileId(), s.candidate().tutor().calendarId(),
                                s.need().calendarId(), OfferHttpFixture.schedule(s.candidate().schedule())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NEED_VERSION_CHANGED"));

        assertThat(offersOf(s)).isZero();
    }

    @Test
    void Q2_an_expired_run_is_409_matching_run_expired() throws Exception {
        Scenario s = fx.scenario();
        jdbc.sql("update matching_runs set expires_at = now() - interval '1 second' where id = ?")
                .param(s.candidate().runId()).update();

        fx.send(s).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATCHING_RUN_EXPIRED"));

        assertThat(offersOf(s)).isZero();
    }

    @Test
    void Q2_terms_that_are_not_those_of_the_run_are_422_for_the_field() throws Exception {
        Scenario s = fx.scenario();
        Candidate c = s.candidate();
        Tutor stranger = fx.tutor("Thầy Nam Thử", s.need().start());
        List<Map<String, String>> moved = new ArrayList<>(c.schedule());
        moved.set(0, Map.of("start", Instant.parse(moved.get(0).get("start")).plus(Duration.ofMinutes(15)).toString(),
                "end", Instant.parse(moved.get(0).get("end")).plus(Duration.ofMinutes(15)).toString()));
        Map<String, String> bodies = Map.of(
                "tutorId", input(s.need().versionId(), c.runId(), stranger.id(), c.tutor().profileId(),
                        c.tutor().calendarId(), s.need().calendarId(), OfferHttpFixture.schedule(c.schedule())),
                "profileVersionId", input(s.need().versionId(), c.runId(), c.tutor().id(), UUID.randomUUID(),
                        c.tutor().calendarId(), s.need().calendarId(), OfferHttpFixture.schedule(c.schedule())),
                "tutorCalendarVersionId", input(s.need().versionId(), c.runId(), c.tutor().id(), c.tutor().profileId(),
                        UUID.randomUUID(), s.need().calendarId(), OfferHttpFixture.schedule(c.schedule())),
                "studentCalendarVersionId", input(s.need().versionId(), c.runId(), c.tutor().id(),
                        c.tutor().profileId(), c.tutor().calendarId(), UUID.randomUUID(),
                        OfferHttpFixture.schedule(c.schedule())),
                "schedule", input(s.need().versionId(), c.runId(), c.tutor().id(), c.tutor().profileId(),
                        c.tutor().calendarId(), s.need().calendarId(), OfferHttpFixture.schedule(moved)));

        for (var entry : bodies.entrySet()) {
            fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"2\"", entry.getValue())
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fields[0].path").value(entry.getKey()));
        }
        assertThat(offersOf(s)).isZero();
    }

    @Test
    void BR05_a_body_without_the_parents_agreement_or_with_too_few_sessions_is_422() throws Exception {
        Scenario s = fx.scenario();
        String json = input(s.need(), s.candidate());

        fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"2\"",
                        json.replace("\"acceptUnchangedTerms\":true", "\"acceptUnchangedTerms\":false"))
                .andExpect(status().isUnprocessableContent());
        fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"2\"",
                        input(s.need().versionId(), s.candidate().runId(), s.candidate().tutor().id(),
                                s.candidate().tutor().profileId(), s.candidate().tutor().calendarId(),
                                s.need().calendarId(), OfferHttpFixture.schedule(s.candidate().schedule().subList(0, 3))))
                .andExpect(status().isUnprocessableContent());

        assertThat(offersOf(s)).isZero();
    }

    @Test
    void Q2_a_tutor_calendar_replaced_or_a_tutor_no_longer_matchable_is_409_offer_terms_changed() throws Exception {
        Scenario replaced = fx.scenario();
        fx.calendar("TUTOR", replaced.candidate().tutor().id(), replaced.need().start());
        fx.send(replaced).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_TERMS_CHANGED"));

        Scenario locked = fx.scenario();
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(locked.candidate().tutor().id()).update();
        fx.send(locked).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_TERMS_CHANGED"));

        // The same version with another rate, which only a direct write can make: the quoted price is checked.
        Scenario repriced = fx.scenario();
        jdbc.sql("update tutor_profiles set rate_vnd_60 = 130000 where id = ?")
                .param(repriced.candidate().tutor().profileId()).update();
        fx.send(repriced).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_TERMS_CHANGED"));

        Scenario child = fx.scenario();
        fx.calendar("STUDENT", child.need().studentId(), child.need().start());
        fx.send(child).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFER_TERMS_CHANGED"));

        assertThat(offersOf(replaced) + offersOf(locked) + offersOf(repriced) + offersOf(child)).isZero();
    }

    @Test
    void BR04_a_session_no_longer_free_is_409_schedule_conflict() throws Exception {
        Scenario s = fx.scenario();
        Map<String, String> first = s.candidate().schedule().getFirst();
        jdbc.sql("""
                insert into availability_windows(id, calendar_id, during, kind)
                values (?, ?, tstzrange(cast(? as timestamptz), cast(? as timestamptz)), 'BLOCKED')
                """).params(UUID.randomUUID(), s.candidate().tutor().calendarId(), first.get("start"), first.get("end"))
                .update();

        fx.send(s).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));

        assertThat(offersOf(s)).isZero();
    }

    // ---------------------------------------------------------------- INV-01, INV-02, INV-06: who may send

    @Test
    void INV_02_another_parent_a_tutor_and_no_session_cannot_send() throws Exception {
        Scenario s = fx.scenario();
        String json = input(s.need(), s.candidate());

        fx.send(newActor(Actor.Role.PARENT), s.need().id(), UUID.randomUUID().toString(), "\"2\"", json)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NEED_NOT_FOUND"));
        fx.send(s.candidate().tutor().actor(), s.need().id(), UUID.randomUUID().toString(), "\"2\"", json)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
        fx.send(null, s.need().id(), UUID.randomUUID().toString(), "\"2\"", json).andExpect(status().isUnauthorized());
        fx.send(s.parent(), UUID.randomUUID(), UUID.randomUUID().toString(), "\"2\"", json)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NEED_NOT_FOUND"));

        assertThat(offersOf(s)).isZero();
    }

    @Test
    void INV_02_a_run_of_another_need_is_404() throws Exception {
        Scenario s = fx.scenario();
        Scenario other = fx.scenario();
        Candidate c = other.candidate();

        fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"2\"",
                        input(s.need().versionId(), c.runId(), c.tutor().id(), c.tutor().profileId(),
                                c.tutor().calendarId(), s.need().calendarId(), OfferHttpFixture.schedule(c.schedule())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCHING_RUN_NOT_FOUND"));
    }

    // ---------------------------------------------------------------- getOffer

    @Test
    void TX06_the_parent_and_the_tutor_read_the_offer_and_nobody_else_does() throws Exception {
        Scenario s = fx.scenario();
        String sent = body(fx.send(s).andExpect(status().isCreated()));
        UUID offer = UUID.fromString(JsonPath.read(sent, "$.id"));

        assertThat(body(fx.read(s.parent(), offer).andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))))
                .isEqualTo(sent);
        assertThat(body(fx.read(s.candidate().tutor().actor(), offer).andExpect(status().isOk()))).isEqualTo(sent);

        fx.read(newActor(Actor.Role.PARENT), offer)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
        fx.read(newActor(Actor.Role.TUTOR), offer)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
        fx.read(newActor(Actor.Role.ADMIN), offer).andExpect(status().isForbidden());
        fx.read(null, offer).andExpect(status().isUnauthorized());
        fx.read(s.parent(), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void Q1_an_overdue_pending_offer_reads_as_expired_before_the_job_writes_it() throws Exception {
        Scenario s = fx.scenario();
        UUID overdue = fx.overdueOffer(s);

        fx.read(s.parent(), overdue).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EXPIRED"))
                .andExpect(jsonPath("$.reasonCode").value("OFFER_EXPIRED"));
        fx.list(s.candidate().tutor().actor(), "").andExpect(jsonPath("$.items[0].state").value("EXPIRED"));

        assertThat(jdbc.sql("select state from class_offers where id = ?").param(overdue).query(String.class).single())
                .isEqualTo("PENDING");
    }

    // ---------------------------------------------------------------- listOffers

    @Test
    void TX06_a_parent_lists_the_offers_of_all_their_children_and_a_tutor_their_received_ones() throws Exception {
        Scenario s = fx.scenario();
        UUID first = id(fx.send(s).andExpect(status().isCreated()));
        // A second child of the same parent, with its own need and another tutor.
        LocalDate start = OfferHttpFixture.farCycle();
        var secondNeed = fx.need(s.parent(), start);
        Tutor secondTutor = fx.tutor("Thầy Nam Thử", start);
        Candidate secondCandidate = fx.run(s.parent(), secondNeed, secondTutor);
        UUID second = id(fx.send(s.parent(), secondNeed.id(), UUID.randomUUID().toString(), "\"2\"",
                input(secondNeed, secondCandidate)).andExpect(status().isCreated()));

        // Newest first, one per page.
        String page1 = body(fx.list(s.parent(), "?limit=1").andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(page1, "$.items[*].id")).containsExactly(second.toString());
        String cursor = JsonPath.read(page1, "$.nextCursor");
        String page2 = body(fx.list(s.parent(), "?limit=1&cursor=" + cursor).andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(page2, "$.items[*].id")).containsExactly(first.toString());
        assertThat(JsonPath.<String>read(page2, "$.nextCursor")).isNull();

        assertThat(JsonPath.<List<String>>read(body(fx.list(s.candidate().tutor().actor(), "")), "$.items[*].id"))
                .containsExactly(first.toString());
        assertThat(JsonPath.<List<String>>read(body(fx.list(secondTutor.actor(), "")), "$.items[*].id"))
                .containsExactly(second.toString());
        assertThat(JsonPath.<List<String>>read(body(fx.list(newActor(Actor.Role.PARENT), "")), "$.items")).isEmpty();
        assertThat(JsonPath.<List<String>>read(body(fx.list(newActor(Actor.Role.TUTOR), "")), "$.items")).isEmpty();
        fx.list(newActor(Actor.Role.ADMIN), "").andExpect(status().isForbidden());
        fx.list(s.parent(), "?limit=51").andExpect(status().isUnprocessableContent());
    }

    @Test
    void INV_06_a_locked_parent_lists_no_offers() throws Exception {
        Scenario s = fx.scenario();
        fx.send(s).andExpect(status().isCreated());
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(s.parent().accountId()).update();

        assertThat(JsonPath.<List<String>>read(body(fx.list(s.parent(), "")), "$.items")).isEmpty();
    }
}
