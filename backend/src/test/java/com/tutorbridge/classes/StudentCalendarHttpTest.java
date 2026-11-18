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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * UC07 student calendar over HTTP (TB-15 part 2): getStudentCalendar, replaceStudentCalendar. The
 * window rules are shared with the tutor's calendar and tested in {@link TutorCalendarHttpTest}; this
 * class covers what differs: the owning parent, the student in the path and the STUDENT lock. Real
 * PostgreSQL, every answer checked against the contract. Fake accounts only.
 */
class StudentCalendarHttpTest extends HttpTest {

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

    private static final String EVENING = window(day(1, 19), day(1, 21), "AVAILABLE");

    private static String path(UUID studentId) {
        return "/api/v1/students/" + studentId + "/calendar";
    }

    private UUID studentOf(Actor parent) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(id, parent.accountId()).update();
        return id;
    }

    private ResultActions replace(Actor actor, UUID studentId, String key, String ifMatch, String ifNoneMatch,
            String json) throws Exception {
        MockHttpServletRequestBuilder request = put(path(studentId)).with(sessions().as(actor))
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

    private ResultActions create(Actor actor, UUID studentId, String json) throws Exception {
        return replace(actor, studentId, newKey(), null, "*", json);
    }

    private ResultActions read(Actor actor, UUID studentId) throws Exception {
        return mvc.perform(get(path(studentId)).with(sessions().as(actor))).andExpect(Contract.matches());
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private int versions(UUID studentId) {
        return jdbc.sql("select count(*) from calendars where owner_kind = 'STUDENT' and owner_id = ?")
                .param(studentId).query(Integer.class).single();
    }

    // ---------------------------------------------------------------- create, read, replace

    @Test
    void the_parent_creates_reads_and_replaces_their_childs_calendar() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        read(parent, student).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CALENDAR_NOT_FOUND"));

        create(parent, student, body(EVENING, window(day(1, 20), day(1, 21), "BLOCKED")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.windows.length()").value(2));
        replace(parent, student, newKey(), "\"1\"", null, body(window(day(2, 18), day(2, 20), "PREFERRED")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""));

        read(parent, student)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.windows.length()").value(1))
                .andExpect(jsonPath("$.windows[0].kind").value("PREFERRED"));
        assertThat(versions(student)).isEqualTo(2);
    }

    @Test
    void each_child_has_their_own_calendar() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID first = studentOf(parent);
        UUID second = studentOf(parent);
        create(parent, first, body(EVENING)).andExpect(status().isOk());

        read(parent, second).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CALENDAR_NOT_FOUND"));
        create(parent, second, body()).andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    void ac_uc07_01_the_window_rules_apply_to_a_student_too() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        create(parent, student, body(window(day(1, 19), day(1, 19), "AVAILABLE")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("windows[0].end"));
        create(parent, student, body(window(day(-1, 19), day(-1, 20), "AVAILABLE")))
                .andExpect(status().isUnprocessableContent());
        assertThat(versions(student)).isZero();
    }

    // ---------------------------------------------------------------- INV-23, INV-20

    @Test
    void inv_23_no_condition_is_428_and_a_stale_version_is_412() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        replace(parent, student, newKey(), null, null, body(EVENING)).andExpect(status().isPreconditionRequired());
        create(parent, student, body(EVENING)).andExpect(status().isOk());
        create(parent, student, body(EVENING)).andExpect(status().isPreconditionFailed());
        replace(parent, student, newKey(), "\"7\"", null, body(EVENING)).andExpect(status().isPreconditionFailed());
        assertThat(versions(student)).isEqualTo(1);
    }

    @Test
    void inv_20_a_replay_returns_the_first_answer_and_the_key_cannot_move_to_another_child() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID sibling = studentOf(parent);
        String key = newKey();
        String first = replace(parent, student, key, null, "*", body(EVENING)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(replace(parent, student, key, null, "*", body(EVENING)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEqualTo(first);
        // Same key and body for the sibling: the path changes the command, so it is a reuse.
        replace(parent, sibling, key, null, "*", body(EVENING))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        replace(parent, student, null, null, "*", body(EVENING)).andExpect(status().isBadRequest());

        assertThat(versions(student)).isEqualTo(1);
        assertThat(versions(sibling)).isZero();
    }

    // ---------------------------------------------------------------- AC-UC07-04, INV-06 access

    @Test
    void ac_uc07_04_another_familys_child_answers_like_a_student_that_does_not_exist() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor stranger = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        create(parent, student, body(EVENING)).andExpect(status().isOk());

        String otherFamily = read(stranger, student).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String unknown = read(stranger, UUID.randomUUID()).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(withoutTrace(otherFamily)).isEqualTo(withoutTrace(unknown));

        replace(stranger, student, newKey(), "\"1\"", null, body(window(day(3, 8), day(3, 9), "BLOCKED")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
        create(stranger, UUID.randomUUID(), body(EVENING)).andExpect(status().isNotFound());
        assertThat(versions(student)).isEqualTo(1);
    }

    private static String withoutTrace(String problem) {
        return problem.replaceAll("\"traceId\":\"[^\"]*\"", "");
    }

    @Test
    void ac_uc07_04_a_tutor_has_no_student_calendar() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor tutor = newActor(Actor.Role.TUTOR);
        UUID student = studentOf(parent);

        read(tutor, student).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_UNAVAILABLE"));
        create(tutor, student, body(EVENING)).andExpect(status().isForbidden());
        assertThat(versions(student)).isZero();
    }

    @Test
    void without_a_session_both_operations_are_401() throws Exception {
        UUID student = studentOf(newActor(Actor.Role.PARENT));
        mvc.perform(get(path(student))).andExpect(status().isUnauthorized()).andExpect(Contract.matches());
        mvc.perform(put(path(student)).header("Idempotency-Key", newKey()).header("If-None-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON).content(body(EVENING)))
                .andExpect(status().isUnauthorized()).andExpect(Contract.matches());
    }

    // ---------------------------------------------------------------- ADR-0007 resource lock

    @Test
    void adr_0007_a_replacement_waits_for_the_student_lock_that_tx05_takes() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        CompletableFuture<Integer> replaced;
        try (Connection gate = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            gate.setAutoCommit(false);
            try (var hold = gate.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                hold.setString(1, "STUDENT:" + student);
                hold.execute();
            }
            replaced = Concurrently.supply(() -> {
                try {
                    return create(parent, student, body(EVENING)).andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20))
                    .until(() -> jdbc.sql("""
                            select count(*) from pg_stat_activity
                            where wait_event_type = 'Lock' and wait_event = 'advisory'
                            """).query(Integer.class).single() >= 1);
            assertThat(versions(student)).isZero();
            gate.commit();
        }
        assertThat(replaced.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(versions(student)).isEqualTo(1);
    }
}
