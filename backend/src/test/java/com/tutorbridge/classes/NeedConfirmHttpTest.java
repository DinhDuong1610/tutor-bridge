package com.tutorbridge.classes;

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
import com.tutorbridge.platform.api.Actor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * confirmNeed over HTTP (TB-16 part 2, TX02) with Đính's Q5 of #16: a complete draft, a start from
 * today to today + 28, and in each of the 4 blocks of 7 days enough non-overlapping future slots in
 * the child's free time (AVAILABLE or PREFERRED, minus BLOCKED). Real PostgreSQL, every answer checked
 * against the contract. Fake accounts and texts only.
 */
class NeedConfirmHttpTest extends HttpTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String GOAL = "Nắm chắc phương trình bậc nhất một ẩn trước kỳ kiểm tra giữa kỳ.";

    private static LocalDate today() {
        return LocalDate.now(VIETNAM);
    }

    private static ZonedDateTime at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(VIETNAM);
    }

    private static String draft(LocalDate start, int duration, int frequency) {
        return """
                {"goal":"%s","budgetVndSession":200000,"durationMinutes":%d,"frequencyPer7Days":%d,
                 "cycleStartDate":"%s"}""".formatted(GOAL, duration, frequency, start);
    }

    private UUID studentOf(Actor parent) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(id, parent.accountId()).update();
        return id;
    }

    private record Window(ZonedDateTime start, ZonedDateTime end, String kind) {}

    /** The child's effective calendar version, as replaceStudentCalendar leaves it (TB-15). */
    private UUID calendar(UUID studentId, List<Window> windows) {
        jdbc.sql("update calendars set effective = false where owner_kind = 'STUDENT' and owner_id = ? and effective")
                .param(studentId).update();
        int version = jdbc.sql("select coalesce(max(version), 0) + 1 from calendars where owner_kind = 'STUDENT' and owner_id = ?")
                .param(studentId).query(Integer.class).single();
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, 'STUDENT', ?, ?, true)")
                .params(id, studentId, version).update();
        for (Window w : windows) {
            jdbc.sql("insert into availability_windows(id, calendar_id, during, kind) values (?, ?, tstzrange(?, ?), ?)")
                    .params(UUID.randomUUID(), id, utc(w.start()), utc(w.end()), w.kind()).update();
        }
        return id;
    }

    private static OffsetDateTime utc(ZonedDateTime time) {
        return time.toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** One window from {@code from} to {@code to} o'clock on each of {@code days} days from {@code first}. */
    private static List<Window> daily(LocalDate first, int days, int from, int to, String kind) {
        return java.util.stream.IntStream.range(0, days)
                .mapToObj(i -> new Window(at(first.plusDays(i), from, 0), at(first.plusDays(i), to, 0), kind))
                .toList();
    }

    private UUID need(Actor parent, UUID studentId, String json) throws Exception {
        String body = mvc.perform(post("/api/v1/students/" + studentId + "/needs").with(sessions().as(parent))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andExpect(Contract.matches())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private ResultActions confirm(Actor actor, UUID needId, String key, String ifMatch, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/needs/" + needId + "/confirm")
                .with(sessions().as(actor)).contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions confirm(Actor actor, UUID needId) throws Exception {
        return confirm(actor, needId, UUID.randomUUID().toString(), "\"1\"", "{\"confirmed\":true}");
    }

    private String needStatus(UUID needId) {
        return jdbc.sql("select status from learning_needs where id = ?").param(needId).query(String.class).single();
    }

    // ---------------------------------------------------------------- confirmed

    @Test
    void TX02_a_complete_draft_with_enough_free_time_is_confirmed_with_the_childs_calendar() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        LocalDate start = today().plusDays(1);
        UUID calendar = calendar(student, daily(start, 28, 19, 21, "AVAILABLE"));
        UUID need = need(parent, student, draft(start, 90, 3));

        confirm(parent, need)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.studentCalendarId").value(calendar.toString()))
                .andExpect(jsonPath("$.draft.goal").value(GOAL));
        assertThat(jdbc.sql("""
                select count(*) from need_versions v join learning_needs n on n.current_version_id = v.id
                where n.id = ? and v.confirmed_at is not null and v.student_calendar_id = ?
                """).params(need, calendar).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void Q5_preferred_windows_count_and_a_start_today_or_at_today_plus_28_is_accepted() throws Exception {
        for (int days : new int[] {0, 28}) {
            Actor parent = newActor(Actor.Role.PARENT);
            UUID student = studentOf(parent);
            LocalDate start = today().plusDays(days);
            // Late evening keeps the first block's slots in the future when the cycle starts today.
            calendar(student, daily(start, 28, 22, 23, "PREFERRED"));
            UUID need = need(parent, student, draft(start, 60, 1));
            confirm(parent, need).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        }
    }

    // ---------------------------------------------------------------- Q5: incomplete or out of range

    @Test
    void Q5_an_incomplete_draft_names_every_missing_field_and_stays_a_draft() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        calendar(student, daily(today().plusDays(1), 28, 19, 21, "AVAILABLE"));
        UUID need = need(parent, student, "{}");

        confirm(parent, need)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[*].path").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "goal", "budgetVndSession", "durationMinutes", "frequencyPer7Days", "cycleStartDate")));
        assertThat(needStatus(need)).isEqualTo("DRAFT");
    }

    @Test
    void Q5_a_goal_under_20_characters_after_trimming_or_a_start_outside_today_to_28_is_422() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate tomorrow = today().plusDays(1);
        String shortGoal = draft(tomorrow, 60, 1).replace(GOAL, "   Ôn tập đại số 8   ");
        for (var c : List.of(new String[] {shortGoal, "goal"},
                new String[] {draft(today().minusDays(1), 60, 1), "cycleStartDate"},
                new String[] {draft(today().plusDays(29), 60, 1), "cycleStartDate"})) {
            UUID student = studentOf(parent);
            calendar(student, daily(today(), 60, 19, 21, "AVAILABLE"));
            UUID need = need(parent, student, c[0]);
            confirm(parent, need).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.fields.length()").value(1))
                    .andExpect(jsonPath("$.fields[0].path").value(c[1]));
            assertThat(needStatus(need)).isEqualTo("DRAFT");
        }
    }

    @Test
    void Q5_a_child_without_a_calendar_is_422() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        UUID need = need(parent, student, draft(today().plusDays(1), 60, 1));

        confirm(parent, need).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields[0].path").value("studentCalendar"));
        assertThat(needStatus(need)).isEqualTo("DRAFT");
    }

    // ---------------------------------------------------------------- Q5: slots per block

    @Test
    void Q5_blocks_without_enough_free_time_are_named_and_nothing_changes() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        LocalDate start = today().plusDays(1);
        calendar(student, daily(start, 14, 19, 21, "AVAILABLE"));
        UUID need = need(parent, student, draft(start, 60, 1));

        confirm(parent, need)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.length()").value(2))
                .andExpect(jsonPath("$.fields[0].path").value("schedule.blocks[2]"))
                .andExpect(jsonPath("$.fields[1].path").value("schedule.blocks[3]"));
        assertThat(needStatus(need)).isEqualTo("DRAFT");
    }

    @Test
    void Q5_blocked_time_is_taken_out_of_the_free_time() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        LocalDate start = today().plusDays(1);
        // 19:00–21:00 free minus 19:30–20:30 blocked leaves two halves of 30 minutes: no 60-minute slot.
        List<Window> windows = new java.util.ArrayList<>(daily(start, 28, 19, 21, "AVAILABLE"));
        for (int i = 0; i < 28; i++) {
            windows.add(new Window(at(start.plusDays(i), 19, 30), at(start.plusDays(i), 20, 30), "BLOCKED"));
        }
        calendar(student, windows);
        UUID need = need(parent, student, draft(start, 60, 1));

        confirm(parent, need).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.length()").value(4));
        assertThat(needStatus(need)).isEqualTo("DRAFT");
    }

    @Test
    void Q5_slots_in_one_window_do_not_overlap() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = today().plusDays(1);
        // One 3-hour window per block holds two 90-minute slots, not three.
        List<Window> weekly = java.util.stream.IntStream.range(0, 4)
                .mapToObj(b -> new Window(at(start.plusDays(7L * b), 18, 0), at(start.plusDays(7L * b), 21, 0),
                        "AVAILABLE"))
                .toList();
        UUID enough = studentOf(parent);
        calendar(enough, weekly);
        confirm(parent, need(parent, enough, draft(start, 90, 2))).andExpect(status().isOk());

        UUID tooFew = studentOf(parent);
        calendar(tooFew, weekly);
        confirm(parent, need(parent, tooFew, draft(start, 90, 3))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.length()").value(4));
    }

    @Test
    void Q5_free_time_already_past_today_does_not_count() throws Exception {
        LocalTime now = LocalTime.now(VIETNAM);
        Assumptions.assumeTrue(now.isAfter(LocalTime.of(2, 0)), "needs two past hours today");
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        LocalDate start = today();
        // Block 0 has free time only today from 00:00 to an hour ago; the other blocks are fine.
        List<Window> windows = new java.util.ArrayList<>();
        windows.add(new Window(at(start, 0, 0), ZonedDateTime.now(VIETNAM).minusHours(1), "AVAILABLE"));
        windows.addAll(daily(start.plusDays(7), 21, 19, 21, "AVAILABLE"));
        calendar(student, windows);
        UUID need = need(parent, student, draft(start, 60, 1));

        confirm(parent, need).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.length()").value(1))
                .andExpect(jsonPath("$.fields[0].path").value("schedule.blocks[0]"));
    }

    // ---------------------------------------------------------------- state, versions, owner

    @Test
    void INV_23_a_stale_or_missing_if_match_and_a_confirmed_or_closed_need_are_refused() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        LocalDate start = today().plusDays(1);
        calendar(student, daily(start, 28, 19, 21, "AVAILABLE"));
        UUID need = need(parent, student, draft(start, 60, 1));

        confirm(parent, need, UUID.randomUUID().toString(), "\"7\"", "{\"confirmed\":true}")
                .andExpect(status().isPreconditionFailed());
        confirm(parent, need, UUID.randomUUID().toString(), null, "{\"confirmed\":true}")
                .andExpect(status().isPreconditionRequired());
        confirm(parent, need, UUID.randomUUID().toString(), "\"1\"", "{\"confirmed\":false}")
                .andExpect(status().isUnprocessableContent());
        confirm(parent, need, UUID.randomUUID().toString(), "\"1\"", "{}")
                .andExpect(status().isUnprocessableContent());
        assertThat(needStatus(need)).isEqualTo("DRAFT");

        confirm(parent, need).andExpect(status().isOk());
        confirm(parent, need, UUID.randomUUID().toString(), "\"2\"", "{\"confirmed\":true}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NEED_ALREADY_CONFIRMED"));
        jdbc.sql("update learning_needs set status = 'CLOSED', revision = 3 where id = ?").param(need).update();
        confirm(parent, need, UUID.randomUUID().toString(), "\"3\"", "{\"confirmed\":true}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NEED_CLOSED"));
    }

    @Test
    void INV_20_INV_06_the_same_key_replays_and_another_parent_gets_404() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor other = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        LocalDate start = today().plusDays(1);
        calendar(student, daily(start, 28, 19, 21, "AVAILABLE"));
        UUID need = need(parent, student, draft(start, 60, 1));
        String key = UUID.randomUUID().toString();

        confirm(other, need, key, "\"1\"", "{\"confirmed\":true}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NEED_NOT_FOUND"));
        confirm(parent, need, key, "\"1\"", "{\"confirmed\":true}").andExpect(status().isOk());
        // The calendar changes afterwards: the replay answers what was written, not a new check.
        calendar(student, List.of());
        confirm(parent, need, key, "\"1\"", "{\"confirmed\":true}").andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        confirm(parent, need, null, "\"1\"", "{\"confirmed\":true}").andExpect(status().isBadRequest());
    }

    @Test
    void ADR_0007_confirm_waits_for_the_childs_resource_lock_so_a_calendar_replacement_cannot_slip_in()
            throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        UUID student = studentOf(parent);
        LocalDate start = today().plusDays(1);
        calendar(student, daily(start, 28, 19, 21, "AVAILABLE"));
        UUID need = need(parent, student, draft(start, 60, 1));
        CompletableFuture<Integer> confirmed;
        try (Connection gate = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            gate.setAutoCommit(false);
            try (var hold = gate.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                hold.setString(1, "STUDENT:" + student);
                hold.execute();
            }
            confirmed = Concurrently.supply(() -> {
                try {
                    return confirm(parent, need).andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> jdbc.sql("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and wait_event = 'advisory'
                    """).query(Integer.class).single() >= 1);
            assertThat(needStatus(need)).isEqualTo("DRAFT");
            gate.commit();
        }
        assertThat(confirmed.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(needStatus(need)).isEqualTo("CONFIRMED");
    }
}
