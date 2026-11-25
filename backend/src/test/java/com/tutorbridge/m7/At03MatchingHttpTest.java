package com.tutorbridge.m7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.tutorbridge.Contract;
import com.tutorbridge.HttpTest;
import com.tutorbridge.platform.api.Actor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AT03 (`08-nghiem-thu-backlog.md`; UC03, UC05): N = 0..6 tutors who pass every hard filter, next to one who
 * fails a hard condition and among them one with 0 months of experience. The run must answer exactly
 * min(5, N) candidates, each once; the failing tutor never; and an order that can be computed again from
 * the parts stored in {@code matching_candidates}: B = 0.625e + 0.375a, ties by e, then by tutor id (BR03,
 * INV-36). The response carries no score, so the score is read back from the stored row.
 *
 * <p>Over HTTP, real PostgreSQL, every answer checked against the contract. Fake accounts only. The
 * database is shared with other tests, so each run puts its cycle in its own 28 days far ahead (further
 * than {@code MatchingRunsHttpTest} goes), where only its own tutors have free time.
 */
class At03MatchingHttpTest extends HttpTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String DESCRIPTION = "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.";
    private static final AtomicInteger CYCLES = new AtomicInteger();
    private static final int BUDGET = 200000;
    private static final int RATE = 150000;

    /**
     * The pool, taken in this order for N = 0..6. Months give e = min(m, 60)/60; {@code fitsPreferred} gives
     * a = 1 (free in all 4 preferred windows) or a = 0 (free in none):
     * <ul>
     * <li>0: 0 months, a = 1 → e = 0, B = 0.375, the newcomer;
     * <li>1: 36 months, a = 0 → e = 0.6, B = 0.375, ties 0 on B and wins on e;
     * <li>2: 60 months, a = 1 → B = 1;
     * <li>3 and 4: 24 months, a = 1 → B = 0.625, a full tie broken by tutor id;
     * <li>5: 12 months, a = 0 → B = 0.125, the one left out at N = 6.
     * </ul>
     */
    private static final int[] MONTHS = {0, 36, 60, 24, 24, 12};
    private static final boolean[] FITS_PREFERRED = {true, false, true, true, true, false};

    @Autowired
    TransactionTemplate tx;

    private record Window(ZonedDateTime start, ZonedDateTime end, String kind) {}

    /** A passing tutor and the parts the test expects for it, worked out here and not by the service. */
    private record Expected(UUID tutorId, BigDecimal e, BigDecimal a, BigDecimal b) {}

    @ParameterizedTest(name = "N = {0}")
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void AT03_n_passing_tutors_give_min_5_n_once_each_in_an_order_the_stored_parts_compute_again(int n)
            throws Exception {
        LocalDate start = farCycle();
        // The child: free 18:00–22:00 every evening, preferring the 4 Thursdays 20:00–22:00.
        List<Window> thursdays = IntStream.range(0, 4)
                .mapToObj(w -> new Window(at(start.plusDays(7L * w + 3), 20), at(start.plusDays(7L * w + 3), 22),
                        "PREFERRED"))
                .toList();
        List<Window> child = new ArrayList<>(daily(start, 18, 22));
        child.addAll(thursdays);
        Actor parent = newActor(Actor.Role.PARENT);
        UUID needId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        need(parent, needId, versionId, start, child);

        // Fails BR02 only (250.000 ₫ > 200.000 ₫) while it would score highest: 60 months and every window.
        UUID tooDear = tutor("Thầy Đắt Thử", 250000, 60, daily(start, 18, 22));
        List<Expected> passing = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // Free 18:00–22:00 holds the Thursday windows (a = 1); free 18:00–20:00 holds none (a = 0).
            UUID id = tutor("Gia sư AT03 " + i, RATE, MONTHS[i], daily(start, 18, FITS_PREFERRED[i] ? 22 : 20));
            BigDecimal e = BigDecimal.valueOf(Math.min(MONTHS[i], 60)).divide(BigDecimal.valueOf(60), 6,
                    RoundingMode.HALF_UP);
            BigDecimal a = FITS_PREFERRED[i] ? new BigDecimal("1.000000") : new BigDecimal("0.000000");
            passing.add(new Expected(id, e, a, score(e, a)));
        }
        List<Expected> expected = passing.stream()
                .sorted(Comparator.comparing(Expected::b).reversed()
                        .thenComparing(Comparator.comparing(Expected::e).reversed())
                        .thenComparing(x -> x.tutorId().toString()))
                .limit(5)
                .toList();

        String body = mvc.perform(post("/api/v1/needs/" + needId + "/matching-runs")
                        .with(sessions().as(parent)).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).header("If-Match", "\"2\"")
                        .content("{\"mode\":\"B\",\"needVersionId\":\"%s\",\"fallbackExplicitlyChosen\":true}"
                                .formatted(versionId)))
                .andExpect(Contract.matches())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.candidates[*].tutor.tutorId");
        List<Integer> ranks = JsonPath.read(body, "$.candidates[*].rank");
        // Exactly min(5, N), each tutor once, ranks 1..k, in the expected order; the failing tutor never.
        assertThat(ids).hasSize(Math.min(5, n)).doesNotHaveDuplicates()
                .containsExactlyElementsOf(expected.stream().map(x -> x.tutorId().toString()).toList())
                .doesNotContain(tooDear.toString());
        assertThat(ranks).containsExactlyElementsOf(IntStream.rangeClosed(1, ids.size()).boxed().toList());
        if (n >= 1) {
            // The newcomer with e = 0 is a real candidate: experience is a score, not a hard filter.
            assertThat(ids).contains(passing.getFirst().tutorId().toString());
        }

        // INV-36: the stored parts are the expected ones, and the stored score is B of the stored parts.
        UUID runId = UUID.fromString(JsonPath.read(body, "$.id"));
        List<Map<String, Object>> rows = jdbc.sql("""
                select tutor_id, rank, e, a, score, s, hard_filters_passed from matching_candidates
                where run_id = ? order by rank
                """).param(runId).query().listOfRows();
        assertThat(rows).hasSize(expected.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            Expected want = expected.get(i);
            BigDecimal e = (BigDecimal) row.get("e");
            BigDecimal a = (BigDecimal) row.get("a");
            assertThat(row.get("tutor_id")).isEqualTo(want.tutorId());
            assertThat(((Number) row.get("rank")).intValue()).isEqualTo(i + 1);
            assertThat(e).isEqualByComparingTo(want.e());
            assertThat(a).isEqualByComparingTo(want.a());
            assertThat((BigDecimal) row.get("score")).isEqualByComparingTo(score(e, a))
                    .isEqualByComparingTo(want.b());
            assertThat(row.get("s")).isNull();
            assertThat(row.get("hard_filters_passed")).isEqualTo(true);
        }
        // The stored order is the order computed again from the stored parts alone.
        List<Map<String, Object>> recomputed = rows.stream()
                .sorted(Comparator.<Map<String, Object>, BigDecimal>comparing(
                                r -> score((BigDecimal) r.get("e"), (BigDecimal) r.get("a"))).reversed()
                        .thenComparing(Comparator.<Map<String, Object>, BigDecimal>comparing(
                                r -> (BigDecimal) r.get("e")).reversed())
                        .thenComparing(r -> r.get("tutor_id").toString()))
                .toList();
        assertThat(recomputed).containsExactlyElementsOf(rows);
    }

    // ---------------------------------------------------------------- fixtures

    private static BigDecimal score(BigDecimal e, BigDecimal a) {
        return new BigDecimal("0.625").multiply(e).add(new BigDecimal("0.375").multiply(a))
                .setScale(6, RoundingMode.HALF_UP);
    }

    /** A cycle start far ahead, past every cycle of MatchingRunsHttpTest, unused by any other run here. */
    private static LocalDate farCycle() {
        return LocalDate.now(VIETNAM).plusDays(5000L + 35L * CYCLES.getAndIncrement());
    }

    private static ZonedDateTime at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(VIETNAM);
    }

    private static OffsetDateTime utc(ZonedDateTime time) {
        return time.toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }

    /** AVAILABLE from {@code from} to {@code to} o'clock on each of the 28 days from {@code first}. */
    private static List<Window> daily(LocalDate first, int from, int to) {
        return IntStream.range(0, 28)
                .mapToObj(i -> new Window(at(first.plusDays(i), from), at(first.plusDays(i), to), "AVAILABLE"))
                .toList();
    }

    private UUID calendar(String ownerKind, UUID ownerId, List<Window> windows) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into calendars(id, owner_kind, owner_id, version, effective) values (?, ?, ?, 1, true)")
                .params(id, ownerKind, ownerId).update();
        for (Window w : windows) {
            jdbc.sql("insert into availability_windows(id, calendar_id, during, kind) values (?, ?, tstzrange(?, ?), ?)")
                    .params(UUID.randomUUID(), id, utc(w.start()), utc(w.end()), w.kind()).update();
        }
        return id;
    }

    /** A CONFIRMED need at revision 2, as confirmNeed leaves it: 60 minutes once a week within the budget. */
    private void need(Actor parent, UUID needId, UUID versionId, LocalDate start, List<Window> windows) {
        UUID student = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .params(student, parent.accountId()).update();
        UUID calendar = calendar("STUDENT", student, windows);
        tx.executeWithoutResult(s -> {
            jdbc.sql("insert into learning_needs(id, student_id, status, revision) values (?, ?, 'CONFIRMED', 2)")
                    .params(needId, student).update();
            jdbc.sql("""
                    insert into need_versions(id, need_id, version, goal, budget_vnd_session, duration_minutes,
                                              frequency, start_date, confirmed_at, student_calendar_id)
                    values (?, ?, 1, 'Nắm chắc phương trình bậc nhất một ẩn trước kỳ kiểm tra.', ?, 60, 1, ?, now(), ?)
                    """).params(versionId, needId, BUDGET, start, calendar).update();
            jdbc.sql("update learning_needs set current_version_id = ? where id = ?").params(versionId, needId)
                    .update();
        });
    }

    /** An ACTIVE tutor with a display name, a current APPROVED profile and an effective calendar. */
    private UUID tutor(String name, int rate, int months, List<Window> windows) {
        Actor tutor = newActor(Actor.Role.TUTOR);
        jdbc.sql("update accounts set display_name = ? where id = ?").params(name, tutor.accountId()).update();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                           current)
                values (?, ?, 1, 'APPROVED', ?, ?, ?, true)
                """).params(UUID.randomUUID(), tutor.accountId(), DESCRIPTION, months, rate).update();
        calendar("TUTOR", tutor.accountId(), windows);
        return tutor.accountId();
    }
}
