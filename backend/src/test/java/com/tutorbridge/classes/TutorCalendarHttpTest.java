package com.tutorbridge.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.Concurrently;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.classes.OfferFixture.Need;
import com.tutorbridge.classes.OfferFixture.Tutor;
import com.tutorbridge.classes.api.AcceptOffer;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.Actor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC07 tutor calendar over HTTP (TB-15): getTutorCalendar, replaceTutorCalendar. Real PostgreSQL,
 * every answer checked against the contract. Fake accounts only.
 */
class TutorCalendarHttpTest extends HttpTest {

    private static final String PATH = "/api/v1/me/tutor-calendar";
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private static ZonedDateTime day(int fromToday, int hour) {
        return LocalDate.now(VIETNAM).plusDays(fromToday).atTime(hour, 0).atZone(VIETNAM);
    }

    private static String window(ZonedDateTime start, ZonedDateTime end, String kind) {
        return "{\"start\":\"%s\",\"end\":\"%s\",\"kind\":\"%s\"}".formatted(start.toInstant(), end.toInstant(), kind);
    }

    private static String body(String... windows) {
        return "{\"timezone\":\"Asia/Ho_Chi_Minh\",\"windows\":[" + String.join(",", windows) + "]}";
    }

    private static final String EVENING = window(day(1, 18), day(1, 21), "AVAILABLE");

    private ResultActions replace(Actor actor, String key, String ifMatch, String ifNoneMatch, String json)
            throws Exception {
        MockHttpServletRequestBuilder request = put(PATH).with(sessions().as(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        if (ifNoneMatch != null) {
            request.header("If-None-Match", ifNoneMatch);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions create(Actor actor, String json) throws Exception {
        return replace(actor, newKey(), null, "*", json);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private int versions(Actor tutor) {
        return jdbc.sql("select count(*) from calendars where owner_kind = 'TUTOR' and owner_id = ?")
                .param(tutor.accountId()).query(Integer.class).single();
    }

    // ---------------------------------------------------------------- create, read, replace

    @Test
    void the_first_calendar_is_created_with_if_none_match_and_read_back_in_order() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        String later = window(day(2, 8), day(2, 10), "PREFERRED");
        String blocked = window(day(1, 19), day(1, 20), "BLOCKED");

        create(tutor, body(later, EVENING, blocked))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.windows.length()").value(3));

        mvc.perform(get(PATH).with(sessions().as(tutor)))
                .andExpect(status().isOk())
                .andExpect(Contract.matches())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.windows[0].kind").value("AVAILABLE"))
                .andExpect(jsonPath("$.windows[1].kind").value("BLOCKED"))
                .andExpect(jsonPath("$.windows[2].kind").value("PREFERRED"));
    }

    @Test
    void a_replacement_is_a_new_version_and_the_old_one_is_kept() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        create(tutor, body(EVENING)).andExpect(status().isOk());

        replace(tutor, newKey(), "\"1\"", null, body(window(day(3, 9), day(3, 11), "AVAILABLE")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.windows.length()").value(1));

        assertThat(versions(tutor)).isEqualTo(2);
        assertThat(jdbc.sql("""
                select count(*) from calendars c join availability_windows w on w.calendar_id = c.id
                where c.owner_kind = 'TUTOR' and c.owner_id = ? and c.version = 1 and not c.effective
                """).param(tutor.accountId()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void overlapping_and_identical_windows_are_accepted() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        create(tutor, body(EVENING, EVENING, window(day(1, 17), day(1, 19), "PREFERRED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windows.length()").value(3));
    }

    @Test
    void an_empty_calendar_is_a_calendar() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        create(tutor, body()).andExpect(status().isOk()).andExpect(jsonPath("$.windows.length()").value(0));
    }

    // ---------------------------------------------------------------- AC-UC07-01

    @Test
    void ac_uc07_01_an_end_not_after_its_start_is_refused() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        create(tutor, body(window(day(1, 18), day(1, 18), "AVAILABLE")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0].path").value("windows[0].end"));
        create(tutor, body(window(day(1, 19), day(1, 18), "AVAILABLE")))
                .andExpect(status().isUnprocessableContent());
        assertThat(versions(tutor)).isZero();
    }

    @Test
    void ac_uc07_01_a_window_outside_today_to_57_days_is_refused() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        create(tutor, body(window(day(-1, 22), day(-1, 23), "AVAILABLE")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("windows[0]"));
        create(tutor, body(window(day(56, 22), day(57, 1), "AVAILABLE")))
                .andExpect(status().isUnprocessableContent());
        // The last whole day of the horizon is accepted, and so is an hour already gone today.
        create(tutor, body(window(day(56, 20), day(56, 22), "AVAILABLE"), window(day(0, 0), day(0, 1), "BLOCKED")))
                .andExpect(status().isOk());
    }

    @Test
    void ac_uc07_01_more_windows_than_the_contract_allows_are_refused() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        String[] many = new String[201];
        for (int i = 0; i < many.length; i++) {
            many[i] = window(day(1 + i / 4, 6 + (i % 4) * 3), day(1 + i / 4, 7 + (i % 4) * 3), "AVAILABLE");
        }
        create(tutor, body(many)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("windows"));
    }

    @Test
    void ac_uc07_01_another_timezone_or_kind_is_refused() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        create(tutor, body(EVENING).replace("Asia/Ho_Chi_Minh", "Asia/Bangkok"))
                .andExpect(status().isUnprocessableContent());
        create(tutor, body(window(day(1, 18), day(1, 19), "BUSY")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("windows[0].kind"));
        assertThat(versions(tutor)).isZero();
    }

    // ---------------------------------------------------------------- INV-23 versions

    @Test
    void inv_23_a_write_without_a_version_condition_is_428() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        replace(tutor, newKey(), null, null, body(EVENING)).andExpect(status().isPreconditionRequired());
    }

    @Test
    void inv_23_a_stale_or_wrong_version_is_412_and_changes_nothing() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        replace(tutor, newKey(), "\"1\"", null, body(EVENING)).andExpect(status().isPreconditionFailed());
        create(tutor, body(EVENING)).andExpect(status().isOk());
        create(tutor, body(EVENING)).andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        replace(tutor, newKey(), "\"1\"", null, body(EVENING)).andExpect(status().isOk());
        replace(tutor, newKey(), "\"1\"", null, body(EVENING)).andExpect(status().isPreconditionFailed());
        assertThat(versions(tutor)).isEqualTo(2);
    }

    // ---------------------------------------------------------------- INV-20 idempotency

    @Test
    void inv_20_a_write_without_a_key_is_400() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        replace(tutor, null, null, "*", body(EVENING)).andExpect(status().isBadRequest());
        assertThat(versions(tutor)).isZero();
    }

    @Test
    void inv_20_the_same_key_and_body_replays_and_another_body_is_409() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        String key = newKey();
        String first = replace(tutor, key, null, "*", body(EVENING)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String again = replace(tutor, key, null, "*", body(EVENING)).andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andReturn().getResponse().getContentAsString();
        assertThat(again).isEqualTo(first);
        assertThat(versions(tutor)).isEqualTo(1);

        replace(tutor, key, null, "*", body(window(day(2, 8), day(2, 9), "AVAILABLE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(versions(tutor)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- AC-UC07-04 access

    @Test
    void ac_uc07_04_each_tutor_sees_only_their_own_calendar() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        Actor other = newActor(Actor.Role.TUTOR);
        create(tutor, body(EVENING)).andExpect(status().isOk());

        mvc.perform(get(PATH).with(sessions().as(other)))
                .andExpect(status().isNotFound())
                .andExpect(Contract.matches())
                .andExpect(jsonPath("$.code").value("CALENDAR_NOT_FOUND"));
    }

    @Test
    void ac_uc07_04_a_parent_has_no_tutor_calendar() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        mvc.perform(get(PATH).with(sessions().as(parent)))
                .andExpect(status().isForbidden()).andExpect(Contract.matches());
        create(parent, body(EVENING)).andExpect(status().isForbidden());
        assertThat(jdbc.sql("select count(*) from calendars where owner_id = ?").param(parent.accountId())
                .query(Integer.class).single()).isZero();
    }

    @Test
    void without_a_session_both_operations_are_401() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(Contract.matches());
        mvc.perform(put(PATH).header("Idempotency-Key", newKey()).header("If-None-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body(EVENING)))
                .andExpect(status().isUnauthorized()).andExpect(Contract.matches());
    }

    @Test
    void a_tutor_account_locked_meanwhile_cannot_replace() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(tutor.accountId()).update();
        create(tutor, body(EVENING)).andExpect(status().isForbidden());
        assertThat(versions(tutor)).isZero();
    }

    // ---------------------------------------------------------------- ADR-0007 resource lock

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD);
    }

    private static void holdTutorLock(Connection gate, Actor tutor) throws Exception {
        gate.setAutoCommit(false);
        try (var hold = gate.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            hold.setString(1, "TUTOR:" + tutor.accountId());
            hold.execute();
        }
    }

    private int advisoryWaiters() {
        return jdbc.sql("""
                select count(*) from pg_stat_activity where wait_event_type = 'Lock' and wait_event = 'advisory'
                """).query(Integer.class).single();
    }

    @Test
    void adr_0007_a_replacement_waits_for_the_resource_lock_that_tx05_takes() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        CompletableFuture<Integer> replaced;
        try (Connection gate = connect()) {
            holdTutorLock(gate, tutor);
            replaced = Concurrently.supply(() -> {
                try {
                    return create(tutor, body(EVENING)).andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20))
                    .until(() -> advisoryWaiters() >= 1);
            assertThat(versions(tutor)).isZero();
            gate.commit();
        }
        assertThat(replaced.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(versions(tutor)).isEqualTo(1);
    }

    @Test
    void adr_0007_a_lock_held_longer_than_the_timeout_is_503_service_busy_and_writes_nothing() throws Exception {
        Actor tutor = newActor(Actor.Role.TUTOR);
        try (Connection gate = connect()) {
            holdTutorLock(gate, tutor);
            create(tutor, body(EVENING))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_BUSY"));
            gate.rollback();
        }
        assertThat(versions(tutor)).isZero();
        assertThat(jdbc.sql("select count(*) from command_receipts where actor_id = ?").param(tutor.accountId())
                .query(Integer.class).single()).isZero();
    }

    // ---------------------------------------------------------------- ADR-0007 race with TX05 (Q1 of #15)

    @Autowired
    AcceptOffer acceptOffer;

    @Autowired
    TransactionTemplate tx;

    /** The result of TX05 in a race: the class it created, or the code it was refused with. */
    private record Accepted(UUID classId, String refusedCode) {}

    private CompletableFuture<Accepted> acceptLater(Tutor tutor, UUID offer) {
        return Concurrently.supply(() -> {
            try {
                return new Accepted(acceptOffer.accept(tutor.id(), offer, 1, newKey()).id(), null);
            } catch (ApiException e) {
                return new Accepted(null, e.code());
            }
        });
    }

    private CompletableFuture<Integer> replaceLater(Actor tutor, String ifMatch, String json) {
        return Concurrently.supply(() -> {
            try {
                return replace(tutor, newKey(), ifMatch, null, json).andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** Available over the whole horizon, but BLOCKED over the first session the fixture offers. */
    private static String blockingTheFirstSession(OfferFixture fixture) {
        return body(window(day(0, 0), day(50, 0), "AVAILABLE"),
                window(fixture.sessionStart(17, 0).toZonedDateTime(), fixture.sessionStart(20, 0).toZonedDateTime(),
                        "BLOCKED"));
    }

    private void awaitAdvisoryWaiters(int count) {
        await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20))
                .until(() -> advisoryWaiters() >= count);
    }

    @Test
    void adr_0007_a_replacement_committed_first_is_what_a_waiting_tx05_checks() throws Exception {
        OfferFixture fixture = new OfferFixture(jdbc, tx);
        Tutor tutor = fixture.tutor();
        Need need = fixture.need(fixture.student());
        UUID offer = fixture.offer(need, tutor);
        Actor actor = new Actor(tutor.id(), Actor.Role.TUTOR, UUID.randomUUID());
        CompletableFuture<Integer> replaced;
        CompletableFuture<Accepted> accepted;
        try (Connection gate = connect()) {
            holdTutorLock(gate, actor);
            // PostgreSQL grants a released advisory lock in queue order: the replacement first, then TX05.
            replaced = replaceLater(actor, "\"1\"", blockingTheFirstSession(fixture));
            awaitAdvisoryWaiters(1);
            accepted = acceptLater(tutor, offer);
            awaitAdvisoryWaiters(2);
            assertThat(fixture.count("select count(*) from classes where accepted_offer_id = ?", offer)).isZero();
            gate.commit();
        }
        assertThat(replaced.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(accepted.get(10, TimeUnit.SECONDS).refusedCode()).isEqualTo("SCHEDULE_CONFLICT");
        assertThat(fixture.count("select count(*) from classes where accepted_offer_id = ?", offer)).isZero();
        assertThat(fixture.count("select count(*) from class_offers where id = ? and state = 'PENDING'", offer))
                .isOne();
        assertThat(fixture.count("""
                select count(*) from calendars where owner_kind = 'TUTOR' and owner_id = ? and effective
                  and version = 2
                """, tutor.id())).isOne();
    }

    @Test
    void uc07_a_replacement_after_tx05_committed_keeps_every_booked_session() throws Exception {
        OfferFixture fixture = new OfferFixture(jdbc, tx);
        Tutor tutor = fixture.tutor();
        Need need = fixture.need(fixture.student());
        UUID offer = fixture.offer(need, tutor);
        Actor actor = new Actor(tutor.id(), Actor.Role.TUTOR, UUID.randomUUID());
        CompletableFuture<Accepted> accepted;
        CompletableFuture<Integer> replaced;
        try (Connection gate = connect()) {
            holdTutorLock(gate, actor);
            // Queue order the other way round: TX05 first, then the replacement that blocks its first session.
            accepted = acceptLater(tutor, offer);
            awaitAdvisoryWaiters(1);
            replaced = replaceLater(actor, "\"1\"", blockingTheFirstSession(fixture));
            awaitAdvisoryWaiters(2);
            gate.commit();
        }
        UUID classId = accepted.get(10, TimeUnit.SECONDS).classId();
        assertThat(classId).isNotNull();
        assertThat(replaced.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        // Control: the sessions and bookings are exactly what TX05 wrote from the offer, all still in force.
        assertThat(fixture.count("""
                select count(*) from lesson_sessions s
                join offer_sessions o on o.offer_id = ? and o.ordinal = s.ordinal and o.during = s.during
                where s.class_id = ? and s.state = 'SCHEDULED' and s.revision = 1
                """, offer, classId)).isEqualTo(4);
        assertThat(fixture.count("select count(*) from lesson_sessions where class_id = ?", classId)).isEqualTo(4);
        assertThat(fixture.count("""
                select count(*) from resource_bookings b join lesson_sessions s on s.id = b.session_id
                where s.class_id = ? and b.active and b.during = s.during
                """, classId)).isEqualTo(8);
        assertThat(fixture.count("""
                select count(*) from availability_windows w join calendars c on c.id = w.calendar_id
                where c.owner_kind = 'TUTOR' and c.owner_id = ? and c.effective and w.kind = 'BLOCKED'
                """, tutor.id())).isOne();
    }
}
