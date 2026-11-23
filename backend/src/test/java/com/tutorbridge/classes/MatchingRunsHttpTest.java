package com.tutorbridge.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Concurrently;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.classes.api.AcceptOffer;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.PostgresTestSupport;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * createMatchingRun and getMatchingRun over HTTP (UC05, TX03, TB-17) with Đính's answers on #17. Real
 * PostgreSQL, every answer checked against the contract. Fake accounts and texts only.
 *
 * <p>The database is shared with other tests, whose tutors may be matchable. Each test therefore puts its
 * cycle in its own 28 days far ahead, where only its own tutors have free time.
 */
class MatchingRunsHttpTest extends HttpTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";
    private static final AtomicInteger CYCLES = new AtomicInteger();

    @Autowired
    AcceptOffer acceptOffer;

    @Autowired
    TransactionTemplate tx;

    // ---------------------------------------------------------------- fixtures

    /** A cycle start far ahead and unused by any other test of this class. */
    private static LocalDate farCycle() {
        return LocalDate.now(VIETNAM).plusDays(120L + 35L * CYCLES.getAndIncrement());
    }

    private static ZonedDateTime at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(VIETNAM);
    }

    private static OffsetDateTime utc(ZonedDateTime time) {
        return time.toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }

    private record Window(ZonedDateTime start, ZonedDateTime end, String kind) {}

    /** One window from {@code from} to {@code to} o'clock on each of the 28 days from {@code first}. */
    private static List<Window> daily(LocalDate first, int from, int to, String kind) {
        return IntStream.range(0, 28)
                .mapToObj(i -> new Window(at(first.plusDays(i), from, 0), at(first.plusDays(i), to, 0), kind))
                .toList();
    }

    private static List<Window> plus(List<Window> a, List<Window> b) {
        List<Window> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private UUID calendar(String ownerKind, UUID ownerId, List<Window> windows) {
        jdbc.sql("update calendars set effective = false where owner_kind = ? and owner_id = ? and effective")
                .params(ownerKind, ownerId).update();
        int version = jdbc.sql("select coalesce(max(version), 0) + 1 from calendars where owner_kind = ? and owner_id = ?")
                .params(ownerKind, ownerId).query(Integer.class).single();
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, ?, ?, ?, true)")
                .params(id, ownerKind, ownerId, version).update();
        for (Window w : windows) {
            jdbc.sql("insert into availability_windows(id, calendar_id, during, kind) values (?, ?, tstzrange(?, ?), ?)")
                    .params(UUID.randomUUID(), id, utc(w.start()), utc(w.end()), w.kind()).update();
        }
        return id;
    }

    private UUID studentOf(Actor parent) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(id, parent.accountId()).update();
        return id;
    }

    private record Need(UUID id, UUID versionId, UUID studentId, UUID calendarId) {}

    /** A CONFIRMED need at revision 2, as confirmNeed leaves it, with the child's calendar. */
    private Need need(Actor parent, LocalDate start, int budget, int duration, int frequency, List<Window> windows) {
        UUID student = studentOf(parent);
        UUID calendar = calendar("STUDENT", student, windows);
        return tx.execute(s -> {
            UUID need = UUID.randomUUID();
            UUID version = UUID.randomUUID();
            jdbc.sql("insert into learning_needs(id, student_id, status, revision) values (?, ?, 'CONFIRMED', 2)")
                    .params(need, student).update();
            jdbc.sql("""
                    insert into need_versions(id, need_id, version, goal, budget_vnd_session, duration_minutes,
                                              frequency, start_date, confirmed_at, student_calendar_id)
                    values (?, ?, 1, 'Nắm chắc phương trình bậc nhất một ẩn trước kỳ kiểm tra.', ?, ?, ?, ?, now(), ?)
                    """).params(version, need, budget, duration, frequency, start, calendar).update();
            jdbc.sql("update learning_needs set current_version_id = ? where id = ?").params(version, need).update();
            return new Need(need, version, student, calendar);
        });
    }

    private record Tutor(UUID id, UUID profileId, UUID calendarId) {}

    /** An ACTIVE tutor with a display name, a current APPROVED profile and, when given, a calendar. */
    private Tutor tutor(String name, int rate, int months, List<Window> windows) {
        Actor tutor = newActor(Actor.Role.TUTOR);
        jdbc.sql("update accounts set display_name = ? where id = ?").params(name, tutor.accountId()).update();
        UUID profile = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current)
                values (?, ?, 1, 'APPROVED', ?, ?, ?, true)
                """).params(profile, tutor.accountId(), DESCRIPTION, months, rate).update();
        UUID calendar = windows == null ? null : calendar("TUTOR", tutor.accountId(), windows);
        return new Tutor(tutor.accountId(), profile, calendar);
    }

    private void verified(UUID profileId) {
        Actor admin = newActor(Actor.Role.ADMIN);
        jdbc.sql("insert into admin_assignments(account_id, capability, granted_by) values (?, 'VERIFY', ?)")
                .params(admin.accountId(), admin.accountId()).update();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to, reviewed_by,
                                                reviewed_at)
                values (?, ?, 'MATH8_ALGEBRA', 'VERIFIED', ?, ?, ?, now())
                """).params(UUID.randomUUID(), profileId, "0".repeat(64), admin.accountId(), admin.accountId()).update();
    }

    private static String b(Need need) {
        return "{\"mode\":\"B\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":true}".formatted(need.versionId());
    }

    private ResultActions run(Actor actor, UUID needId, String key, String ifMatch, String json) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/needs/" + needId + "/matching-runs")
                .with(sessions().as(actor)).contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mvc.perform(request).andExpect(Contract.matches());
    }

    private ResultActions run(Actor parent, Need need) throws Exception {
        return run(parent, need.id(), UUID.randomUUID().toString(), "\"2\"", b(need));
    }

    private ResultActions read(Actor actor, UUID runId) throws Exception {
        return mvc.perform(get("/api/v1/matching-runs/" + runId).with(sessions().as(actor))).andExpect(Contract.matches());
    }

    private static List<String> tutorIds(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.candidates[*].tutor.tutorId");
    }

    private static <T> List<T> list(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private int runsOf(Need need) {
        return jdbc.sql("select count(*) from matching_runs where need_version_id = ?").param(need.versionId())
                .query(Integer.class).single();
    }

    /** The audit events of the run a response shows, as "type/outcome". */
    private List<String> eventsOf(String body) {
        UUID runId = UUID.fromString(JsonPath.read(body, "$.id"));
        return jdbc.sql("select event_type || '/' || outcome from audit_events where object_id = ? order by created_at")
                .param(runId).query(String.class).list();
    }

    // ---------------------------------------------------------------- AC-UC05-01: min(5, N), no duplicates

    @Test
    void AC_UC05_01_six_passing_tutors_give_the_best_five_ranked_once_each() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 90, 2, daily(start, 18, 22, "AVAILABLE"));
        List<Tutor> tutors = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            // Experience 0, 12, ... 60 months: the one with 0 (e = 0) is the one left out.
            tutors.add(tutor("Gia sư thử " + i, 120000, 12 * i, daily(start, 17, 23, "AVAILABLE")));
        }

        ResultActions result = run(parent, need)
                .andExpect(status().isAccepted())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.mode").value("B"))
                .andExpect(jsonPath("$.failureCode").doesNotExist())
                .andExpect(jsonPath("$.needVersionId").value(need.versionId().toString()))
                .andExpect(jsonPath("$.studentCalendarVersionId").value(need.calendarId().toString()))
                .andExpect(jsonPath("$.candidates.length()").value(5))
                .andExpect(jsonPath("$.candidates[*].rank").value(org.hamcrest.Matchers.contains(1, 2, 3, 4, 5)));

        assertThat(tutorIds(result)).containsExactly(tutors.get(5).id().toString(), tutors.get(4).id().toString(),
                tutors.get(3).id().toString(), tutors.get(2).id().toString(), tutors.get(1).id().toString());
        // 90 minutes at 120.000 ₫/60 = 180.000 ₫; 2 a week for 4 weeks = 8 sessions.
        assertThat(MatchingRunsHttpTest.<Integer>list(body(result), "$.candidates[*].priceVndSession")).containsOnly(180000);
        assertThat(MatchingRunsHttpTest.<Integer>list(body(result), "$.candidates[*].commonSchedule.length()")).containsOnly(8);
    }

    @Test
    void AC_UC05_01_fewer_passing_tutors_than_five_give_exactly_those() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        Tutor one = tutor("Cô Lan Thử", 150000, 24, daily(start, 18, 22, "AVAILABLE"));
        Tutor two = tutor("Thầy Nam Thử", 150000, 36, daily(start, 18, 22, "AVAILABLE"));

        assertThat(tutorIds(run(parent, need).andExpect(status().isAccepted())))
                .containsExactly(two.id().toString(), one.id().toString());
    }

    // ---------------------------------------------------------------- AC-UC05-02: every hard filter

    @Test
    void AC_UC05_02_each_missing_or_wrong_hard_condition_leaves_the_tutor_out() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        List<Window> evenings = daily(start, 18, 22, "AVAILABLE");
        Need need = need(parent, start, 150000, 90, 1, evenings);

        Tutor ok = tutor("Cô Lan Thử", 100000, 24, evenings);
        // 90 minutes at 100.001 ₫/60 = 150.002 ₫ > 150.000 ₫ (BR02).
        tutor("Thầy Đắt Thử", 100001, 60, evenings);
        tutor("Thầy Không Lịch Thử", 100000, 60, null);
        // No free time in the third week (BR04: every block).
        List<Window> gap = evenings.stream().filter(w -> {
            LocalDate day = w.start().toLocalDate();
            return day.isBefore(start.plusDays(14)) || !day.isBefore(start.plusDays(21));
        }).toList();
        tutor("Cô Thiếu Tuần Thử", 100000, 60, gap);
        // Evenings blocked every day: BLOCKED wins over AVAILABLE (Q5 of #15).
        tutor("Cô Bận Thử", 100000, 60, plus(evenings, daily(start, 17, 23, "BLOCKED")));
        // Free only in the morning, when the child is not.
        tutor("Thầy Sáng Thử", 100000, 60, daily(start, 7, 11, "AVAILABLE"));
        Tutor waiting = tutor("Cô Chờ Duyệt Thử", 100000, 60, evenings);
        jdbc.sql("update tutor_profiles set status = 'PENDING', current = false where id = ?").param(waiting.profileId())
                .update();
        Tutor locked = tutor("Thầy Khóa Thử", 100000, 60, evenings);
        jdbc.sql("update accounts set status = 'LOCKED' where id = ?").param(locked.id()).update();
        Tutor nameless = tutor("Tạm Thử", 100000, 60, evenings);
        jdbc.sql("update accounts set display_name = null where id = ?").param(nameless.id()).update();

        assertThat(tutorIds(run(parent, need).andExpect(status().isAccepted()))).containsExactly(ok.id().toString());
    }

    @Test
    void AC_UC05_02_time_already_booked_by_a_class_is_not_offered_again() throws Exception {
        // A real class through acceptOffer: its bookings cover Monday 18:00–19:30 of each week (OfferFixture).
        OfferFixture fixture = new OfferFixture(jdbc, tx);
        OfferFixture.Tutor booked = fixture.tutor();
        jdbc.sql("update accounts set display_name = 'Cô Đã Có Lớp Thử' where id = ?").param(booked.id()).update();
        OfferFixture.Need other = fixture.need(fixture.student());
        UUID offer = fixture.offer(other, booked, fixture.sessionStart(18, 0));
        acceptOffer.accept(booked.id(), offer, 1, UUID.randomUUID().toString());

        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = fixture.cycleStart();
        Need need = need(parent, start, 200000, 90, 1, daily(start, 18, 20, "AVAILABLE"));

        ResultActions result = run(parent, need).andExpect(status().isAccepted());

        String body = body(result);
        List<String> ids = JsonPath.read(body, "$.candidates[*].tutor.tutorId");
        int index = ids.indexOf(booked.id().toString());
        assertThat(index).as("the tutor still has other evenings").isNotNegative();
        List<String> starts = JsonPath.read(body, "$.candidates[" + index + "].commonSchedule[*].start");
        List<Instant> bookedStarts = jdbc.sql("""
                select lower(during) from resource_bookings where resource_kind = 'TUTOR' and resource_id = ? and active
                """).param(booked.id()).query(OffsetDateTime.class).list().stream().map(OffsetDateTime::toInstant).toList();
        assertThat(bookedStarts).hasSize(4);
        assertThat(starts.stream().map(Instant::parse)).doesNotContainAnyElementsOf(bookedStarts);
        // The first evening of the cycle is the booked Monday, so each week starts on Tuesday instead.
        assertThat(Instant.parse(starts.getFirst())).isEqualTo(at(start.plusDays(1), 18, 0).toInstant());
    }

    @Test
    void AC_UC05_02_time_the_student_already_has_a_class_is_not_offered_with_another_tutor() throws Exception {
        // BR04 for the student's side: a real class through acceptOffer books the child on the first evening
        // of each week, 18:00–19:30 (OfferFixture). A new need of the same child, with a tutor who has no
        // booking at all, must still leave those evenings out.
        OfferFixture fixture = new OfferFixture(jdbc, tx);
        OfferFixture.Student child = fixture.student();
        OfferFixture.Tutor classTutor = fixture.tutor();
        UUID offer = fixture.offer(fixture.need(child), classTutor, fixture.sessionStart(18, 0));
        acceptOffer.accept(classTutor.id(), offer, 1, UUID.randomUUID().toString());

        LocalDate start = fixture.cycleStart();
        calendar("STUDENT", child.id(), daily(start, 18, 20, "AVAILABLE"));
        OfferFixture.Need next = fixture.need(child);
        // 60 months and a fair rate, so this tutor is among the five whatever else is free then.
        Tutor free = tutor("Cô Rảnh Tối Thử", 120000, 60, daily(start, 18, 20, "AVAILABLE"));
        Actor parent = new Actor(child.parentId(), Actor.Role.PARENT, UUID.randomUUID());

        String body = body(run(parent, next.id(), UUID.randomUUID().toString(), "\"1\"",
                "{\"mode\":\"B\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":true}".formatted(next.versionId()))
                .andExpect(status().isAccepted()));

        List<String> ids = JsonPath.read(body, "$.candidates[*].tutor.tutorId");
        int index = ids.indexOf(free.id().toString());
        assertThat(index).as("the tutor is free on the other evenings").isNotNegative();
        List<Instant> starts = JsonPath.<List<String>>read(body, "$.candidates[" + index + "].commonSchedule[*].start")
                .stream().map(Instant::parse).toList();
        List<Instant> childBooked = jdbc.sql("""
                select lower(during) from resource_bookings where resource_kind = 'STUDENT' and resource_id = ? and active
                """).param(child.id()).query(OffsetDateTime.class).list().stream().map(OffsetDateTime::toInstant).toList();
        assertThat(childBooked).hasSize(4);
        assertThat(jdbc.sql("select count(*) from resource_bookings where resource_id = ? and active")
                .param(free.id()).query(Integer.class).single()).as("only the child is booked").isZero();
        assertThat(starts).doesNotContainAnyElementsOf(childBooked);
        // 90 minutes fit an 18:00–20:00 evening only from 18:00 to 18:30, all inside the booked 18:00–19:30,
        // so each week starts on the day after the class instead.
        for (int week = 0; week < 4; week++) {
            assertThat(starts.get(week)).isEqualTo(at(start.plusDays(7L * week + 1), 18, 0).toInstant());
        }
    }

    // ---------------------------------------------------------------- AC-UC05-03: deterministic, recomputable

    @Test
    void AC_UC05_03_the_same_data_gives_the_same_order_and_ties_go_to_the_lower_tutor_id() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        List<Tutor> tied = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tied.add(tutor("Gia sư hòa " + i, 150000, 24, daily(start, 18, 22, "AVAILABLE")));
        }
        List<String> expected = tied.stream().map(t -> t.id().toString()).sorted().toList();

        List<String> first = tutorIds(run(parent, need).andExpect(status().isAccepted()));
        List<String> second = tutorIds(run(parent, need).andExpect(status().isAccepted()));

        assertThat(first).containsExactlyElementsOf(expected);
        assertThat(second).containsExactlyElementsOf(first);
        // The stored parts give the stored score again: B = 0.625e + 0.375a, 6 decimals (BR03).
        List<Map<String, Object>> rows = jdbc.sql("""
                select c.e, c.a, c.score, c.s from matching_candidates c join matching_runs r on r.id = c.run_id
                where r.need_version_id = ?
                """).param(need.versionId()).query().listOfRows();
        assertThat(rows).hasSize(6).allSatisfy(row -> {
            BigDecimal e = (BigDecimal) row.get("e");
            BigDecimal a = (BigDecimal) row.get("a");
            assertThat(e).isEqualByComparingTo("0.4");
            assertThat(a).isEqualByComparingTo("0.5");
            assertThat((BigDecimal) row.get("score")).isEqualByComparingTo(
                    new BigDecimal("0.625").multiply(e).add(new BigDecimal("0.375").multiply(a))
                            .setScale(6, java.math.RoundingMode.HALF_UP));
            assertThat(row.get("s")).isNull();
        });
        assertThat(jdbc.sql("select distinct algorithm_version || '/' || config_version from matching_runs where need_version_id = ?")
                .param(need.versionId()).query(String.class).list()).containsExactly("B-v1/none");
    }

    // ---------------------------------------------------------------- AC-UC05-04: reasons, empty vs failed

    @Test
    void AC_UC05_04_reasons_say_the_price_self_declared_experience_verified_scope_and_preferred_windows()
            throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        // Free every evening; preferred on the 4 Thursdays 20:00–22:00 and on the 4 Saturdays 7:00–9:00.
        List<Window> thursdays = IntStream.range(0, 4)
                .mapToObj(w -> new Window(at(start.plusDays(7L * w + 3), 20, 0), at(start.plusDays(7L * w + 3), 22, 0),
                        "PREFERRED")).toList();
        List<Window> saturdays = IntStream.range(0, 4)
                .mapToObj(w -> new Window(at(start.plusDays(7L * w + 5), 7, 0), at(start.plusDays(7L * w + 5), 9, 0),
                        "PREFERRED")).toList();
        Need need = need(parent, start, 200000, 90, 1, plus(daily(start, 18, 22, "AVAILABLE"), plus(thursdays, saturdays)));
        // The tutor is free in the evenings only: the 4 Thursday windows fit, the 4 Saturday mornings do not.
        Tutor tutor = tutor("Cô Lan Thử", 100000, 7, daily(start, 18, 22, "AVAILABLE"));
        verified(tutor.profileId());

        String body = body(run(parent, need).andExpect(status().isAccepted()));

        assertThat(MatchingRunsHttpTest.<String>list(body, "$.candidates[0].reasons")).containsExactly(
                "Giá 150.000 ₫ một buổi 90 phút (100.000 ₫/60 phút), trong ngân sách 200.000 ₫/buổi.",
                "Kinh nghiệm 7 tháng (tự khai).",
                "Đã xác minh: Toán 8 đại số.",
                "Khớp 4/8 khung giờ ưu tiên.");
        assertThat((String) JsonPath.read(body, "$.candidates[0].tutor.experienceLabel")).isEqualTo("SELF_DECLARED");
        assertThat(MatchingRunsHttpTest.<String>list(body, "$.candidates[0].tutor.verificationScopes"))
                .containsExactly("MATH8_ALGEBRA");
        // Each week's session is the preferred Thursday, not the earlier Monday.
        List<String> starts = JsonPath.read(body, "$.candidates[0].commonSchedule[*].start");
        assertThat(starts.stream().map(s -> Instant.parse(s).atZone(VIETNAM).getDayOfWeek()).distinct())
                .containsExactly(start.plusDays(3).getDayOfWeek());
        assertThat(body).doesNotContain("email").doesNotContain("evidence");
    }

    @Test
    void AC_UC05_04_without_preferred_windows_a_is_one_half_and_the_window_line_is_left_out() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        tutor("Cô Lan Thử", 150000, 60, daily(start, 18, 22, "AVAILABLE"));

        String body = body(run(parent, need).andExpect(status().isAccepted()));

        assertThat(MatchingRunsHttpTest.<String>list(body, "$.candidates[0].reasons")).containsExactly(
                "Giá 150.000 ₫ một buổi 60 phút (150.000 ₫/60 phút), trong ngân sách 200.000 ₫/buổi.",
                "Kinh nghiệm 60 tháng (tự khai).");
        assertThat(jdbc.sql("""
                select c.a from matching_candidates c join matching_runs r on r.id = c.run_id where r.need_version_id = ?
                """).param(need.versionId()).query(BigDecimal.class).single()).isEqualByComparingTo("0.5");
    }

    @Test
    void AC_UC05_04_no_passing_tutor_is_an_empty_completed_run_not_a_failure() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        // Nobody else is free in this cycle, and this tutor costs too much (AT02: nothing is relaxed).
        tutor("Thầy Đắt Thử", 500000, 60, daily(start, 18, 22, "AVAILABLE"));

        String body = body(run(parent, need)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.candidates.length()").value(0))
                .andExpect(jsonPath("$.failureCode").doesNotExist()));
        // INV-38: an empty B run is still a use of B, with its own event.
        assertThat(eventsOf(body)).containsExactly("MATCHING_FALLBACK_USED/COMPLETED");
    }

    @Test
    void Q1_mode_S_while_OP_11_is_open_is_a_failed_run_with_its_own_code_and_event() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        tutor("Cô Lan Thử", 150000, 60, daily(start, 18, 22, "AVAILABLE"));

        String body = body(run(parent, need.id(), UUID.randomUUID().toString(), "\"2\"",
                "{\"mode\":\"S\",\"needVersionId\":\"%s\"}".formatted(need.versionId()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mode").value("S"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("AI_RANKING_UNAVAILABLE"))
                .andExpect(jsonPath("$.candidates.length()").value(0)));

        UUID runId = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(jdbc.sql("select event_type || '/' || outcome from audit_events where object_id = ?").param(runId)
                .query(String.class).list()).containsExactly("MATCHING_AI_UNAVAILABLE/FAILED");
        read(parent, runId).andExpect(status().isOk()).andExpect(jsonPath("$.failureCode").value("AI_RANKING_UNAVAILABLE"));
    }

    @Test
    void Q2_INV_38_mode_B_needs_the_explicit_choice_and_a_refusal_leaves_nothing() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        String key = UUID.randomUUID().toString();

        for (String json : List.of("{\"mode\":\"B\",\"needVersionId\":\"%s\"}",
                "{\"mode\":\"B\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":false}")) {
            run(parent, need.id(), key, "\"2\"", json.formatted(need.versionId()))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.fields[0].path").value("fallbackExplicitlyChosen"));
        }
        assertThat(runsOf(need)).isZero();
        // Nothing was kept for that key, so the corrected body goes through with it.
        run(parent, need.id(), key, "\"2\"", b(need)).andExpect(status().isAccepted());
    }

    @Test
    void the_body_follows_the_contract() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));

        for (String json : List.of("{\"mode\":\"X\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":true}",
                "{\"mode\":\"B\",\"fallbackExplicitlyChosen\":true}",
                "{\"mode\":\"B\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":true,\"other\":1}")) {
            run(parent, need.id(), UUID.randomUUID().toString(), "\"2\"", json.formatted(need.versionId()))
                    .andExpect(status().isUnprocessableContent());
        }
        assertThat(runsOf(need)).isZero();
    }

    // ---------------------------------------------------------------- the need and its version

    @Test
    void a_need_that_is_not_confirmed_or_an_old_version_is_refused_without_a_run() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));

        run(parent, need.id(), UUID.randomUUID().toString(), "\"2\"",
                "{\"mode\":\"B\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":true}".formatted(UUID.randomUUID()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NEED_VERSION_CHANGED"));
        jdbc.sql("update learning_needs set status = 'DRAFT' where id = ?").param(need.id()).update();
        run(parent, need).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NEED_VERSION_CHANGED"));
        assertThat(runsOf(need)).isZero();
    }

    @Test
    void INV_23_a_stale_or_missing_if_match_is_refused_without_a_run() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));

        run(parent, need.id(), UUID.randomUUID().toString(), "\"1\"", b(need)).andExpect(status().isPreconditionFailed());
        run(parent, need.id(), UUID.randomUUID().toString(), null, b(need)).andExpect(status().isPreconditionRequired());
        assertThat(runsOf(need)).isZero();
    }

    @Test
    void TX03_a_run_waits_for_an_edit_of_the_need_holding_its_row_and_then_sees_the_new_revision() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        tutor("Cô Lan Thử", 150000, 60, daily(start, 18, 22, "AVAILABLE"));
        CompletableFuture<Integer> ran;
        try (Connection edit = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            edit.setAutoCommit(false);
            // What updateNeedDraft does: a new draft version, then the need row moved to it with the next
            // revision. Moving current_version_id is what a run waiting on the row must still answer 412 to,
            // not 404 (Codex, #215 review).
            UUID next = UUID.randomUUID();
            try (var version = edit.prepareStatement(
                    "insert into tutorbridge.need_versions(id, need_id, version, goal) values (?, ?, 2, ?)")) {
                version.setObject(1, next);
                version.setObject(2, need.id());
                version.setString(3, "Ôn lại phương trình bậc nhất và bài toán thực tế.");
                version.executeUpdate();
            }
            try (var hold = edit.prepareStatement("""
                    update tutorbridge.learning_needs set revision = revision + 1, status = 'DRAFT',
                           current_version_id = ? where id = ?""")) {
                hold.setObject(1, next);
                hold.setObject(2, need.id());
                hold.executeUpdate();
            }
            ran = Concurrently.supply(() -> {
                try {
                    return run(parent, need).andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            // The run really waits on the row lock (FOR SHARE against the edit's FOR UPDATE).
            await().atMost(Duration.ofSeconds(4)).pollInterval(Duration.ofMillis(20)).until(() -> jdbc.sql("""
                    select count(*) from pg_stat_activity
                    where wait_event_type = 'Lock' and wait_event in ('transactionid', 'tuple')
                    """).query(Integer.class).single() >= 1);
            assertThat(runsOf(need)).isZero();
            edit.commit();
        }
        // The If-Match "2" is now stale: nothing is computed on the old version, and no receipt is kept.
        assertThat(ran.get(10, TimeUnit.SECONDS)).isEqualTo(412);
        assertThat(runsOf(need)).isZero();
        assertThat(jdbc.sql("select count(*) from command_receipts where actor_id = ? and command_type = ?")
                .params(parent.accountId(), "CREATE_MATCHING_RUN").query(Integer.class).single()).isZero();
    }

    @Test
    void TX03_ADR_0007_a_need_row_held_past_the_lock_timeout_is_503_and_the_same_key_works_afterwards()
            throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        tutor("Cô Lan Thử", 150000, 60, daily(start, 18, 22, "AVAILABLE"));
        String key = UUID.randomUUID().toString();
        try (Connection edit = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            edit.setAutoCommit(false);
            // An edit of the need that holds its row longer than the 5 second lock_timeout (Codex, #215).
            try (var hold = edit.prepareStatement("select 1 from tutorbridge.learning_needs where id = ? for update")) {
                hold.setObject(1, need.id());
                hold.executeQuery();
            }
            // On its own thread and bounded, so a run that waited without limit fails here instead of hanging.
            CompletableFuture<MockHttpServletResponse> busy = Concurrently.supply(() -> {
                try {
                    return run(parent, need.id(), key, "\"2\"", b(need)).andReturn().getResponse();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            try {
                MockHttpServletResponse answer = busy.get(15, TimeUnit.SECONDS);
                assertThat(answer.getStatus()).isEqualTo(503);
                assertThat((String) JsonPath.read(answer.getContentAsString(), "$.code")).isEqualTo("SERVICE_BUSY");
            } finally {
                edit.rollback();
            }
        }
        // All of it rolled back: no run, no receipt, no event.
        assertThat(runsOf(need)).isZero();
        assertThat(jdbc.sql("select count(*) from command_receipts where actor_id = ?").param(parent.accountId())
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from audit_events where actor_id = ?").param(parent.accountId())
                .query(Integer.class).single()).isZero();
        // #83: the client resends with the same key once the row is free, and that makes the run.
        String body = body(run(parent, need.id(), key, "\"2\"", b(need)).andExpect(status().isAccepted()));
        assertThat(runsOf(need)).isOne();
        assertThat(eventsOf(body)).containsExactly("MATCHING_FALLBACK_USED/COMPLETED");
    }

    /** Runs one create while {@code holder} keeps its locks; bounded, so a wait without limit fails the test. */
    private MockHttpServletResponse runWhileHeld(Connection holder, Actor parent, Need need, String key)
            throws Exception {
        CompletableFuture<MockHttpServletResponse> pending = Concurrently.supply(() -> {
            try {
                return run(parent, need.id(), key, "\"2\"", b(need)).andReturn().getResponse();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        try {
            return pending.get(15, TimeUnit.SECONDS);
        } finally {
            holder.rollback();
        }
    }

    private void assertNothingWrittenFor(Actor parent, Need need) {
        assertThat(runsOf(need)).isZero();
        assertThat(jdbc.sql("select count(*) from command_receipts where actor_id = ?").param(parent.accountId())
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from audit_events where actor_id = ?").param(parent.accountId())
                .query(Integer.class).single()).isZero();
    }

    @Test
    void TX03_ADR_0007_a_receipt_held_by_another_request_with_the_same_key_is_503_too() throws Exception {
        // The timeout is set before the receipt, so the wait on the receipt's unique key is bounded as well
        // (Codex, #215 review of a724c86).
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        tutor("Cô Lan Thử", 150000, 60, daily(start, 18, 22, "AVAILABLE"));
        String key = UUID.randomUUID().toString();
        MockHttpServletResponse answer;
        try (Connection holder = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            holder.setAutoCommit(false);
            // Another request with this key that has written its receipt and not committed yet.
            try (var receipt = holder.prepareStatement("""
                    insert into tutorbridge.command_receipts(id, actor_id, command_type, idempotency_key, payload_sha256,
                                                             state, expires_at)
                    values (?, ?, 'CREATE_MATCHING_RUN', ?, repeat('0', 64), 'PROCESSING', now() + interval '7 days')
                    """)) {
                receipt.setObject(1, UUID.randomUUID());
                receipt.setObject(2, parent.accountId());
                receipt.setString(3, key);
                receipt.executeUpdate();
            }
            answer = runWhileHeld(holder, parent, need, key);
        }
        assertThat(answer.getStatus()).isEqualTo(503);
        assertThat((String) JsonPath.read(answer.getContentAsString(), "$.code")).isEqualTo("SERVICE_BUSY");
        assertNothingWrittenFor(parent, need);
        run(parent, need.id(), key, "\"2\"", b(need)).andExpect(status().isAccepted());
        assertThat(runsOf(need)).isOne();
    }

    @Test
    void TX03_ADR_0007_a_candidate_waiting_on_its_tutor_account_rolls_back_the_run_and_is_503() throws Exception {
        // Writing a candidate takes a key share lock on the tutor's account (foreign key); an account held FOR
        // UPDATE, as submitTutorProfile holds it, makes that wait. Past the timeout the run already inserted
        // is rolled back with the receipt (Codex, #215 review of a724c86).
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        Tutor busyTutor = tutor("Cô Bận Hồ Sơ Thử", 150000, 60, daily(start, 18, 22, "AVAILABLE"));
        String key = UUID.randomUUID().toString();
        MockHttpServletResponse answer;
        try (Connection holder = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            holder.setAutoCommit(false);
            try (var account = holder.prepareStatement("select 1 from tutorbridge.accounts where id = ? for update")) {
                account.setObject(1, busyTutor.id());
                account.executeQuery();
            }
            answer = runWhileHeld(holder, parent, need, key);
        }
        assertThat(answer.getStatus()).isEqualTo(503);
        assertThat((String) JsonPath.read(answer.getContentAsString(), "$.code")).isEqualTo("SERVICE_BUSY");
        assertNothingWrittenFor(parent, need);
        String body = body(run(parent, need.id(), key, "\"2\"", b(need)).andExpect(status().isAccepted()));
        assertThat(JsonPath.<List<String>>read(body, "$.candidates[*].tutor.tutorId"))
                .containsExactly(busyTutor.id().toString());
    }

    // ---------------------------------------------------------------- idempotency

    @Test
    void INV_20_the_same_key_and_body_answer_the_same_run_and_another_body_is_refused() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        tutor("Cô Lan Thử", 150000, 60, daily(start, 18, 22, "AVAILABLE"));
        String key = UUID.randomUUID().toString();

        String first = body(run(parent, need.id(), key, "\"2\"", b(need)).andExpect(status().isAccepted()));
        // A tutor added afterwards changes nothing for a replay: it answers the stored run.
        tutor("Thầy Nam Thử", 150000, 60, daily(start, 18, 22, "AVAILABLE"));
        String again = body(run(parent, need.id(), key, "\"2\"", b(need)).andExpect(status().isAccepted()));

        assertThat(again).isEqualTo(first);
        assertThat(runsOf(need)).isOne();
        // BR03, INV-38: the use of B is its own event, recorded once; the replay adds none.
        assertThat(eventsOf(first)).containsExactly("MATCHING_FALLBACK_USED/COMPLETED");
        run(parent, need.id(), key, "\"2\"", "{\"mode\":\"S\",\"needVersionId\":\"%s\"}".formatted(need.versionId()))
                .andExpect(status().isConflict());
        run(parent, need.id(), null, "\"2\"", b(need)).andExpect(status().isBadRequest());
        assertThat(runsOf(need)).isOne();
    }

    @Test
    void INV_20_two_requests_with_one_key_at_the_same_moment_make_one_run() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        tutor("Cô Lan Thử", 150000, 60, daily(start, 18, 22, "AVAILABLE"));
        String key = UUID.randomUUID().toString();

        List<String> ids = Concurrently.atOnce(4, i -> JsonPath.read(
                body(run(parent, need.id(), key, "\"2\"", b(need)).andExpect(status().isAccepted())), "$.id"));

        assertThat(ids).hasSize(4).containsOnly(ids.getFirst());
        assertThat(runsOf(need)).isOne();
    }

    // ---------------------------------------------------------------- access (INV-06)

    @Test
    void INV_06_another_family_or_an_unknown_id_is_the_same_404_and_other_roles_are_403() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        Actor stranger = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        UUID runId = UUID.fromString(JsonPath.read(body(run(parent, need).andExpect(status().isAccepted())), "$.id"));

        run(stranger, need).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NEED_NOT_FOUND"));
        run(stranger, UUID.randomUUID(), UUID.randomUUID().toString(), "\"2\"", b(need))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NEED_NOT_FOUND"));
        read(stranger, runId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCHING_RUN_NOT_FOUND"));
        read(parent, UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCHING_RUN_NOT_FOUND"));
        for (Actor.Role role : List.of(Actor.Role.TUTOR, Actor.Role.ADMIN)) {
            Actor other = newActor(role);
            run(other, need).andExpect(status().isForbidden());
            read(other, runId).andExpect(status().isForbidden());
        }
        assertThat(runsOf(need)).isOne();
    }

    // ---------------------------------------------------------------- getMatchingRun

    @Test
    void Q7_a_stored_run_shows_the_profile_version_and_price_it_was_made_with() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));
        Tutor tutor = tutor("Cô Lan Thử", 150000, 60, daily(start, 18, 22, "AVAILABLE"));
        String created = body(run(parent, need).andExpect(status().isAccepted()));
        UUID runId = UUID.fromString(JsonPath.read(created, "$.id"));

        // The tutor's next version is approved afterwards with another rate.
        jdbc.sql("update tutor_profiles set current = false, status = 'RETIRED' where id = ?").param(tutor.profileId()).update();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current)
                values (?, ?, 2, 'APPROVED', ?, 60, 180000, true)
                """).params(UUID.randomUUID(), tutor.id(), DESCRIPTION).update();

        String read = body(read(parent, runId).andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\"")));
        assertThat(read).isEqualTo(created);
        assertThat((String) JsonPath.read(read, "$.candidates[0].tutor.profileVersionId")).isEqualTo(tutor.profileId().toString());
        assertThat((Integer) JsonPath.read(read, "$.candidates[0].tutor.rateVnd60")).isEqualTo(150000);
        assertThat((String) JsonPath.read(read, "$.candidates[0].tutorCalendarVersionId")).isEqualTo(tutor.calendarId().toString());
    }

    @Test
    void Q5_a_run_expires_24_hours_after_it_was_made() throws Exception {
        Actor parent = newActor(Actor.Role.PARENT);
        LocalDate start = farCycle();
        Need need = need(parent, start, 200000, 60, 1, daily(start, 18, 22, "AVAILABLE"));

        String body = body(run(parent, need).andExpect(status().isAccepted()));

        UUID runId = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(jdbc.sql("select expires_at - created_at = interval '24 hours' from matching_runs where id = ?")
                .param(runId).query(Boolean.class).single()).isTrue();
    }
}
