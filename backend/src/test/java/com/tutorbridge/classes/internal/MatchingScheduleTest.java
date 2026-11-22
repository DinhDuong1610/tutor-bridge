package com.tutorbridge.classes.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorbridge.classes.internal.MatchingSchedule.Interval;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The pure part of TX03 (BR03, BR04; Đính's Q3 and Q4 on #17), without a database. */
class MatchingScheduleTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    /** A Monday, so the blocks start on Mondays. */
    private static final LocalDate START = LocalDate.of(2026, 12, 7);
    private static final Instant BEFORE = START.minusDays(1).atStartOfDay(VIETNAM).toInstant();

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(VIETNAM).toInstant();
    }

    private static Interval on(int dayOfCycle, int fromHour, int fromMinute, int toHour, int toMinute) {
        LocalDate day = START.plusDays(dayOfCycle);
        return new Interval(at(day, fromHour, fromMinute), at(day, toHour, toMinute));
    }

    /** The same window on every day of the 28 day cycle. */
    private static List<Interval> daily(int fromHour, int toHour) {
        return IntStream.range(0, 28).mapToObj(d -> on(d, fromHour, 0, toHour, 0)).toList();
    }

    @Test
    void Q3_each_block_takes_the_earliest_days_one_session_a_day() {
        List<Interval> sessions = MatchingSchedule.sessions(START, 90, 2, BEFORE, daily(19, 22), List.of()).orElseThrow();

        assertThat(sessions).hasSize(8);
        // Monday and Tuesday of each week, at 19:00 for 90 minutes.
        for (int week = 0; week < 4; week++) {
            assertThat(sessions.get(2 * week)).isEqualTo(on(7 * week, 19, 0, 20, 30));
            assertThat(sessions.get(2 * week + 1)).isEqualTo(on(7 * week + 1, 19, 0, 20, 30));
        }
    }

    @Test
    void Q3_sessions_start_on_a_quarter_hour() {
        List<Interval> free = IntStream.range(0, 28).mapToObj(d -> on(d, 19, 7, 21, 0)).toList();

        List<Interval> sessions = MatchingSchedule.sessions(START, 60, 1, BEFORE, free, List.of()).orElseThrow();

        assertThat(sessions).allSatisfy(s -> assertThat(s.start().atZone(VIETNAM).toLocalTime().toString()).isEqualTo("19:15"));
    }

    @Test
    void Q3_a_day_in_a_preferred_window_comes_before_an_earlier_day_outside_one() {
        // Free every evening; preferred only on Thursdays 20:00–22:00.
        List<Interval> preferred = IntStream.range(0, 4).mapToObj(w -> on(7 * w + 3, 20, 0, 22, 0)).toList();

        List<Interval> sessions = MatchingSchedule.sessions(START, 60, 2, BEFORE, daily(18, 22), preferred).orElseThrow();

        // Each block: Monday 18:00 (earliest other day) and Thursday 20:00 (preferred), in time order.
        assertThat(sessions.subList(0, 2)).containsExactly(on(0, 18, 0, 19, 0), on(3, 20, 0, 21, 0));
    }

    @Test
    void BR04_a_block_without_enough_days_fails_the_whole_tutor() {
        // Free every day but in the third week only on Monday.
        List<Interval> free = new ArrayList<>(daily(19, 21));
        free.removeIf(i -> i.start().isAfter(on(15, 0, 0, 0, 0).start()) && i.start().isBefore(on(21, 0, 0, 0, 0).start()));

        assertThat(MatchingSchedule.sessions(START, 60, 1, BEFORE, free, List.of())).isPresent();
        assertThat(MatchingSchedule.sessions(START, 60, 2, BEFORE, free, List.of())).isEmpty();
    }

    @Test
    void BR04_one_long_window_still_gives_only_one_session_that_day() {
        List<Interval> free = List.of(new Interval(on(0, 0, 0, 0, 0).start(), on(28, 0, 0, 0, 0).start()));

        List<Interval> sessions = MatchingSchedule.sessions(START, 60, 3, BEFORE, free, List.of()).orElseThrow();

        assertThat(sessions).hasSize(12);
        assertThat(sessions.stream().map(s -> s.start().atZone(VIETNAM).toLocalDate()).distinct()).hasSize(12);
    }

    @Test
    void BR04_time_before_now_is_not_offered() {
        Instant now = at(START, 19, 40);

        // 19:45 is the next quarter hour after now; 60 minutes still fit before 21:00 that day.
        List<Interval> sessions = MatchingSchedule.sessions(START, 60, 1, now, daily(19, 21), List.of()).orElseThrow();
        assertThat(sessions.getFirst()).isEqualTo(on(0, 19, 45, 20, 45));
        // 90 minutes do not fit after 19:45, so the first session moves to the next day.
        assertThat(MatchingSchedule.sessions(START, 90, 1, now, daily(19, 21), List.of()).orElseThrow().getFirst())
                .isEqualTo(on(1, 19, 0, 20, 30));
    }

    @Test
    void Q4_a_preferred_window_fits_when_it_holds_one_whole_session_of_free_time() {
        List<Interval> free = List.of(on(0, 19, 0, 20, 0), on(1, 19, 0, 21, 0));
        List<Interval> preferred = List.of(on(0, 19, 0, 22, 0), on(1, 20, 0, 22, 0), on(2, 19, 0, 22, 0));

        assertThat(MatchingSchedule.fittingWindows(preferred, free, 60)).isEqualTo(2);
        assertThat(MatchingSchedule.fittingWindows(preferred, free, 90)).isZero();
    }

    @Test
    void BR03_a_is_the_share_of_fitting_windows_and_one_half_without_any() {
        assertThat(MatchingSchedule.a(2, 3)).isEqualByComparingTo("0.666667");
        assertThat(MatchingSchedule.a(0, 0)).isEqualByComparingTo("0.5");
        assertThat(MatchingSchedule.fittingFrom(new BigDecimal("0.666667"), 3)).isEqualTo(2);
        assertThat(MatchingSchedule.fittingFrom(new BigDecimal("0.142857"), 7)).isEqualTo(1);
    }

    @Test
    void BR03_e_caps_at_sixty_months_and_b_is_rounded_from_the_stored_parts() {
        assertThat(MatchingSchedule.e(0)).isEqualByComparingTo("0");
        assertThat(MatchingSchedule.e(24)).isEqualByComparingTo("0.4");
        assertThat(MatchingSchedule.e(120)).isEqualByComparingTo("1");
        assertThat(MatchingSchedule.e(7)).isEqualByComparingTo("0.116667");
        // 0.625 × 0.116667 + 0.375 × 0.666667 = 0.072916875 + 0.250000125 = 0.322917
        assertThat(MatchingSchedule.b(new BigDecimal("0.116667"), new BigDecimal("0.666667"))).isEqualByComparingTo("0.322917");
    }

    @Test
    void BR02_the_session_price_rounds_up() {
        assertThat(MatchingSchedule.sessionPrice(100_000, 90)).isEqualTo(150_000);
        assertThat(MatchingSchedule.sessionPrice(100_001, 90)).isEqualTo(150_002);
        assertThat(MatchingSchedule.sessionPrice(100_000, 60)).isEqualTo(100_000);
    }
}
