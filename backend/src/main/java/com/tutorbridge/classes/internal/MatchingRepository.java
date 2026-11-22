package com.tutorbridge.classes.internal;

import com.tutorbridge.classes.internal.MatchingSchedule.Interval;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code matching_runs}, {@code matching_candidates}, {@code matching_candidate_sessions} (V15) and the
 * calendar reads of TX03. All in the classes module (module-map); accounts and profiles come from
 * {@code Parties}.
 */
@Repository
class MatchingRepository {

    /** The confirmed need version a run is made for. Null fields only on a need that is not CONFIRMED. */
    record NeedVersion(UUID needId, UUID studentId, long revision, String status, UUID versionId,
            Integer budgetVndSession, Integer durationMinutes, Integer frequency, LocalDate cycleStartDate,
            UUID studentCalendarId) {}

    /** One tutor's effective calendar and the common free time with the student. */
    record TutorFree(UUID calendarId, List<Interval> free) {}

    record RunRow(UUID id, UUID needVersionId, String mode, String status, String failureCode, Instant expiresAt,
            UUID needId, UUID studentId, int budgetVndSession, int durationMinutes, LocalDate cycleStartDate,
            UUID studentCalendarId) {}

    record CandidateRow(UUID tutorId, UUID profileId, UUID tutorCalendarId, int rank, BigDecimal e, BigDecimal a,
            BigDecimal score, int quotedPrice) {}

    private static final String NEED = """
            select n.id as need_id, n.student_id, n.revision, n.status, v.id as version_id,
                   v.budget_vnd_session, v.duration_minutes, v.frequency, v.start_date, v.student_calendar_id
            from learning_needs n join need_versions v on v.id = n.current_version_id
            where n.id = ?
            """;

    private final JdbcClient jdbc;

    MatchingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * TX03 step 2: the need row FOR SHARE, so update and confirm (FOR UPDATE) wait until the run commits and
     * cannot move the version under it, while two runs of one need do not wait on each other.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<NeedVersion> lockNeedShared(UUID needId) {
        return jdbc.sql(NEED + " for share of n").param(needId).query(MatchingRepository::needVersion).optional();
    }

    /** The student's PREFERRED windows inside the cycle, overlapping ones merged (Q4). */
    @Transactional(propagation = Propagation.MANDATORY)
    List<Interval> preferredWindows(UUID calendarId, Interval cycle) {
        return jdbc.sql("""
                with preferred as (
                    select coalesce(range_agg(during), '{}'::tstzmultirange) as m
                    from availability_windows where calendar_id = ? and kind = 'PREFERRED'
                )
                select lower(r) as start_at, upper(r) as end_at
                from preferred, unnest(preferred.m * tstzmultirange(tstzrange(?, ?))) as r
                order by 1
                """).params(calendarId, at(cycle.start()), at(cycle.end()))
                .query(MatchingRepository::interval).list();
    }

    /**
     * BR04 for one page of tutors in one statement: free time of the student's calendar ∩ free time of each
     * tutor's effective calendar (not BLOCKED, minus BLOCKED, as TX05 reads a calendar), minus the active
     * bookings of the student and of that tutor, inside [from, to). A tutor without an effective calendar
     * is missing from the map: missing data is not a pass (INV-35).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Map<UUID, TutorFree> commonFree(UUID studentId, UUID studentCalendarId, Collection<UUID> tutorIds, Instant from,
            Instant to) {
        Map<UUID, TutorFree> byTutor = new LinkedHashMap<>();
        if (tutorIds.isEmpty() || !to.isAfter(from)) {
            return byTutor;
        }
        UUID[] tutors = tutorIds.toArray(UUID[]::new);
        jdbc.sql("""
                with span as (select tstzrange(:from, :to) as r),
                student as (
                    select coalesce(range_agg(w.during) filter (where w.kind <> 'BLOCKED'), '{}'::tstzmultirange)
                         - coalesce(range_agg(w.during) filter (where w.kind = 'BLOCKED'), '{}'::tstzmultirange)
                         - coalesce((select range_agg(b.during) from resource_bookings b, span
                                     where b.resource_kind = 'STUDENT' and b.resource_id = :student and b.active
                                       and b.during && span.r), '{}'::tstzmultirange) as m
                    from availability_windows w where w.calendar_id = :studentCalendar
                ),
                tutor as (
                    select c.owner_id as tutor_id, c.id as calendar_id,
                           coalesce(range_agg(w.during) filter (where w.kind <> 'BLOCKED'), '{}'::tstzmultirange)
                         - coalesce(range_agg(w.during) filter (where w.kind = 'BLOCKED'), '{}'::tstzmultirange) as m
                    from calendars c left join availability_windows w on w.calendar_id = c.id
                    where c.owner_kind = 'TUTOR' and c.effective and c.owner_id = any(:tutors)
                    group by c.owner_id, c.id
                ),
                booked as (
                    select b.resource_id as tutor_id, range_agg(b.during) as m
                    from resource_bookings b, span
                    where b.resource_kind = 'TUTOR' and b.active and b.resource_id = any(:tutors) and b.during && span.r
                    group by b.resource_id
                )
                select t.tutor_id, t.calendar_id, lower(piece) as start_at, upper(piece) as end_at
                from tutor t cross join student s cross join span
                left join booked k on k.tutor_id = t.tutor_id
                left join lateral unnest((s.m * t.m - coalesce(k.m, '{}'::tstzmultirange))
                                         * tstzmultirange(span.r)) as piece on true
                order by t.tutor_id, start_at
                """).param("from", at(from)).param("to", at(to)).param("student", studentId)
                .param("studentCalendar", studentCalendarId).param("tutors", tutors)
                .query((ResultSet rs) -> {
                    UUID tutor = rs.getObject("tutor_id", UUID.class);
                    TutorFree entry = byTutor.computeIfAbsent(tutor,
                            t -> new TutorFree(uuid(rs, "calendar_id"), new ArrayList<>()));
                    OffsetDateTime start = rs.getObject("start_at", OffsetDateTime.class);
                    if (start != null) {
                        entry.free().add(new Interval(start.toInstant(),
                                rs.getObject("end_at", OffsetDateTime.class).toInstant()));
                    }
                });
        return byTutor;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void insertRun(UUID id, UUID needVersionId, String mode, String algorithmVersion, String status,
            String failureCode, Instant createdAt, Instant expiresAt) {
        jdbc.sql("""
                insert into matching_runs(id, need_version_id, mode, algorithm_version, config_version, status,
                                          failure_code, created_at, expires_at)
                values (?, ?, ?, ?, 'none', ?, ?, ?, ?)
                """).params(id, needVersionId, mode, algorithmVersion, status, failureCode,
                        at(createdAt), at(expiresAt)).update();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void insertCandidate(UUID runId, CandidateRow candidate, List<Interval> sessions) {
        jdbc.sql("""
                insert into matching_candidates(run_id, tutor_id, profile_id, tutor_calendar_id, rank, s, e, a, score,
                                                quoted_price, hard_filters_passed)
                values (?, ?, ?, ?, ?, null, ?, ?, ?, ?, true)
                """).params(runId, candidate.tutorId(), candidate.profileId(), candidate.tutorCalendarId(),
                        candidate.rank(), candidate.e(), candidate.a(), candidate.score(), candidate.quotedPrice())
                .update();
        for (int i = 0; i < sessions.size(); i++) {
            jdbc.sql("""
                    insert into matching_candidate_sessions(run_id, tutor_id, ordinal, during)
                    values (?, ?, ?, tstzrange(?, ?, '[)'))
                    """).params(runId, candidate.tutorId(), i + 1, at(sessions.get(i).start()),
                            at(sessions.get(i).end())).update();
        }
    }

    /** A run with the need version it was made for; the caller checks the owner. */
    Optional<RunRow> findRun(UUID runId) {
        return jdbc.sql("""
                select r.id, r.need_version_id, r.mode, r.status, r.failure_code, r.expires_at,
                       v.need_id, n.student_id, v.budget_vnd_session, v.duration_minutes, v.start_date,
                       v.student_calendar_id
                from matching_runs r
                join need_versions v on v.id = r.need_version_id
                join learning_needs n on n.id = v.need_id
                where r.id = ?
                """).param(runId).query((rs, n) -> new RunRow(uuid(rs, "id"), uuid(rs, "need_version_id"),
                        rs.getString("mode"), rs.getString("status"), rs.getString("failure_code"),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant(), uuid(rs, "need_id"),
                        uuid(rs, "student_id"), rs.getInt("budget_vnd_session"), rs.getInt("duration_minutes"),
                        rs.getObject("start_date", LocalDate.class), uuid(rs, "student_calendar_id")))
                .optional();
    }

    List<CandidateRow> candidates(UUID runId) {
        return jdbc.sql("""
                select tutor_id, profile_id, tutor_calendar_id, rank, e, a, score, quoted_price
                from matching_candidates where run_id = ? order by rank
                """).param(runId).query((rs, n) -> new CandidateRow(uuid(rs, "tutor_id"), uuid(rs, "profile_id"),
                        uuid(rs, "tutor_calendar_id"), rs.getInt("rank"), rs.getBigDecimal("e"), rs.getBigDecimal("a"),
                        rs.getBigDecimal("score"), rs.getInt("quoted_price")))
                .list();
    }

    /** Each candidate's sessions in order. */
    Map<UUID, List<Interval>> sessions(UUID runId) {
        Map<UUID, List<Interval>> byTutor = new LinkedHashMap<>();
        jdbc.sql("""
                select tutor_id, lower(during) as start_at, upper(during) as end_at
                from matching_candidate_sessions where run_id = ? order by tutor_id, ordinal
                """).param(runId).query((ResultSet rs) -> {
                    byTutor.computeIfAbsent(uuid(rs, "tutor_id"), t -> new ArrayList<>()).add(interval(rs, 0));
                });
        return byTutor;
    }

    private static NeedVersion needVersion(ResultSet rs, int n) throws SQLException {
        return new NeedVersion(uuid(rs, "need_id"), uuid(rs, "student_id"), rs.getLong("revision"),
                rs.getString("status"), uuid(rs, "version_id"), rs.getObject("budget_vnd_session", Integer.class),
                rs.getObject("duration_minutes", Integer.class), rs.getObject("frequency", Integer.class),
                rs.getObject("start_date", LocalDate.class), uuid(rs, "student_calendar_id"));
    }

    private static Interval interval(ResultSet rs, int n) throws SQLException {
        return new Interval(rs.getObject("start_at", OffsetDateTime.class).toInstant(),
                rs.getObject("end_at", OffsetDateTime.class).toInstant());
    }

    private static UUID uuid(ResultSet rs, String column) {
        try {
            return rs.getObject(column, UUID.class);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
