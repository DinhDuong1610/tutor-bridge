package com.tutorbridge.classes.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The pure part of TX03 for one candidate (TB-17; BR03, BR04; Đính's Q3 and Q4 on #17): the common
 * schedule, the share of preferred windows that fit, and the B score. No database, no clock: the caller
 * passes the free time and {@code now}, so the same data always gives the same answer (AC-UC05-03).
 */
final class MatchingSchedule {

    /** Sessions start on a quarter hour. Vietnam is UTC+7 all year, so its quarter hours are UTC's. */
    static final long STEP_SECONDS = 15 * 60;
    static final int BLOCKS = NeedService.BLOCKS;
    static final int SCALE = 6;

    private MatchingSchedule() {}

    /** A finite interval [start, end). */
    record Interval(Instant start, Instant end) {
        boolean contains(Interval other) {
            return !other.start().isBefore(start) && !other.end().isAfter(end);
        }
    }

    /** Midnight in Vietnam at the start of {@code day}. */
    static Instant midnight(LocalDate day) {
        return day.atStartOfDay(CalendarService.VIETNAM).toInstant();
    }

    static Interval cycle(LocalDate cycleStart) {
        return new Interval(midnight(cycleStart), midnight(cycleStart.plusDays(7L * BLOCKS)));
    }

    /**
     * Q3 (a): in each block of 7 days, {@code frequency} sessions of {@code durationMinutes}, at most one a
     * day (Vietnam), starting on a quarter hour, inside {@code free} and after {@code now}. Each day offers
     * its earliest session inside a preferred window if it has one, else its earliest session. Days with a
     * preferred session are taken first, earliest first, then the other days, earliest first. Empty when
     * a block cannot hold enough sessions: the tutor does not pass (BR04). The sessions come in time order.
     *
     * @param free disjoint, ordered common free time
     * @param preferred disjoint, ordered preferred windows of the student
     */
    static Optional<List<Interval>> sessions(LocalDate cycleStart, int durationMinutes, int frequency, Instant now,
            List<Interval> free, List<Interval> preferred) {
        long length = durationMinutes * 60L;
        List<Interval> inPreferred = intersect(free, preferred);
        List<Interval> chosen = new ArrayList<>();
        for (int block = 0; block < BLOCKS; block++) {
            List<Interval> preferredDays = new ArrayList<>();
            List<Interval> otherDays = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                LocalDate date = cycleStart.plusDays(7L * block + day);
                Instant from = max(midnight(date), now);
                Instant to = midnight(date.plusDays(1));
                Optional<Interval> best = earliest(inPreferred, from, to, length);
                if (best.isPresent()) {
                    preferredDays.add(best.get());
                } else {
                    earliest(free, from, to, length).ifPresent(otherDays::add);
                }
            }
            List<Interval> picked = new ArrayList<>(preferredDays);
            picked.addAll(otherDays);
            if (picked.size() < frequency) {
                return Optional.empty();
            }
            chosen.addAll(picked.subList(0, frequency));
        }
        chosen.sort(Comparator.comparing(Interval::start));
        return Optional.of(List.copyOf(chosen));
    }

    /**
     * Q4: how many preferred windows still hold one whole session in the common free time. A window fits
     * when one session that {@link #sessions} could choose lies inside it: starting on a quarter hour, of
     * {@code durationMinutes}, inside one free interval and inside one day (Vietnam). A window counts
     * whether or not its session is the one chosen.
     *
     * @param free disjoint, ordered common free time
     */
    static int fittingWindows(List<Interval> preferred, List<Interval> free, int durationMinutes) {
        long length = durationMinutes * 60L;
        int fitting = 0;
        for (Interval window : preferred) {
            if (holdsSession(intersect(List.of(window), free), length)) {
                fitting++;
            }
        }
        return fitting;
    }

    /** Whether one interval of {@code parts} holds a session as {@link #sessions} places one, day by day. */
    private static boolean holdsSession(List<Interval> parts, long length) {
        for (Interval part : parts) {
            for (LocalDate day = part.start().atZone(CalendarService.VIETNAM).toLocalDate();
                    midnight(day).isBefore(part.end()); day = day.plusDays(1)) {
                if (earliest(List.of(part), midnight(day), midnight(day.plusDays(1)), length).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** a = k/n, or 0.5 without preferred windows (BR03), rounded to 6 decimals as stored. */
    static BigDecimal a(int fitting, int windows) {
        if (windows == 0) {
            return new BigDecimal("0.500000");
        }
        return BigDecimal.valueOf(fitting).divide(BigDecimal.valueOf(windows), SCALE, RoundingMode.HALF_UP);
    }

    /** k back from the stored a and n, for the reason line (n is small, so a keeps k exactly). */
    static int fittingFrom(BigDecimal a, int windows) {
        return a.multiply(BigDecimal.valueOf(windows)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** e = min(months, 60) / 60, rounded to 6 decimals as stored. */
    static BigDecimal e(int experienceMonths) {
        return BigDecimal.valueOf(Math.min(experienceMonths, 60)).divide(BigDecimal.valueOf(60), SCALE,
                RoundingMode.HALF_UP);
    }

    /** B = 0.625e + 0.375a from the stored, rounded parts, so the score can be computed again from the row. */
    static BigDecimal b(BigDecimal e, BigDecimal a) {
        return new BigDecimal("0.625").multiply(e).add(new BigDecimal("0.375").multiply(a))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** {@code ceil(rate × D / 60)}, the session price of BR02 and of {@code class_offers}. */
    static int sessionPrice(int rateVnd60, int durationMinutes) {
        return (int) Math.ceilDiv((long) rateVnd60 * durationMinutes, 60L);
    }

    /** The earliest quarter-hour session of {@code length} seconds inside one interval of {@code free}, within [from, to). */
    private static Optional<Interval> earliest(List<Interval> free, Instant from, Instant to, long length) {
        for (Interval part : free) {
            Instant lo = max(part.start(), from);
            Instant hi = min(part.end(), to);
            // Round up from the exact instant: a fraction of a second after a quarter hour is past it, so a
            // now of 19:00:00.001 never gives a session at 19:00:00. hi rounds down, which only shortens.
            long seconds = lo.getEpochSecond() + (lo.getNano() > 0 ? 1 : 0);
            long start = Math.ceilDiv(seconds, STEP_SECONDS) * STEP_SECONDS;
            if (start + length <= hi.getEpochSecond()) {
                return Optional.of(new Interval(Instant.ofEpochSecond(start), Instant.ofEpochSecond(start + length)));
            }
        }
        return Optional.empty();
    }

    /** The overlap of two disjoint ordered lists, itself disjoint and ordered. */
    static List<Interval> intersect(List<Interval> left, List<Interval> right) {
        List<Interval> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < left.size() && j < right.size()) {
            Instant lo = max(left.get(i).start(), right.get(j).start());
            Instant hi = min(left.get(i).end(), right.get(j).end());
            if (hi.isAfter(lo)) {
                out.add(new Interval(lo, hi));
            }
            if (left.get(i).end().isBefore(right.get(j).end())) {
                i++;
            } else {
                j++;
            }
        }
        return out;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
