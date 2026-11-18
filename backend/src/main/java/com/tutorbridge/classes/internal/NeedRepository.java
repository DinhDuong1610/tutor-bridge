package com.tutorbridge.classes.internal;

import com.tutorbridge.platform.api.Cursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code learning_needs} and {@code need_versions} (V1, module classes). A confirmed version is never
 * updated here: only the unconfirmed current version of a DRAFT need is edited in place (UC04).
 * Callers check the owner.
 */
@Repository
class NeedRepository {

    /** The fields of {@code NeedDraftInput}; null is empty. */
    record Draft(String goal, Integer budgetVndSession, Integer durationMinutes, Integer frequencyPer7Days,
            LocalDate cycleStartDate) {}

    /** A need with its current version. */
    record Row(UUID id, UUID studentId, long revision, String status, Instant createdAt, UUID versionId,
            int version, boolean confirmed, UUID studentCalendarId, Draft draft) {}

    private static final String SELECT = """
            select n.id, n.student_id, n.revision, n.status, n.created_at, v.id as version_id, v.version,
                   v.confirmed_at is not null as confirmed, v.student_calendar_id, v.goal, v.budget_vnd_session,
                   v.duration_minutes, v.frequency, v.start_date
            from learning_needs n join need_versions v on v.id = n.current_version_id
            """;

    private final JdbcClient jdbc;

    NeedRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Row> find(UUID needId) {
        return jdbc.sql(SELECT + " where n.id = ?").param(needId).query(NeedRepository::row).optional();
    }

    /**
     * The need row under FOR UPDATE, the "business entity" step of INV-16 that TX05 takes too. The
     * current version is read in the same statement; it only changes under this lock.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Row> lock(UUID needId) {
        return jdbc.sql(SELECT + " where n.id = ? for update of n").param(needId).query(NeedRepository::row)
                .optional();
    }

    /** Q4 of #16: at most one need per child that is not CLOSED. Read under the child's resource lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean hasOpenNeed(UUID studentId) {
        return jdbc.sql("select exists(select 1 from learning_needs where student_id = ? and status <> 'CLOSED')")
                .param(studentId).query(Boolean.class).single();
    }

    /** Newest first, so the open need comes before the closed ones. */
    List<Row> listByStudent(UUID studentId, Cursor after, int limit) {
        String position = after == null ? "" : "and (n.created_at, n.id) < (:afterAt, :afterId)";
        var query = jdbc.sql(SELECT + """
                where n.student_id = :student %s
                order by n.created_at desc, n.id desc
                limit :limit
                """.formatted(position)).param("student", studentId).param("limit", limit);
        if (after != null) {
            query = query.param("afterAt", at(after.at())).param("afterId", after.id());
        }
        return query.query(NeedRepository::row).list();
    }

    /** A new DRAFT need and its first version, current from the start (the foreign key is deferred). */
    @Transactional(propagation = Propagation.MANDATORY)
    void insert(UUID needId, UUID versionId, UUID studentId, Draft draft, Instant createdAt) {
        jdbc.sql("""
                insert into learning_needs(id, student_id, revision, status, created_at, current_version_id)
                values (?, ?, 1, 'DRAFT', ?, ?)
                """).params(needId, studentId, at(createdAt), versionId).update();
        insertVersion(versionId, needId, 1, draft);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void insertVersion(UUID versionId, UUID needId, int version, Draft draft) {
        jdbc.sql("""
                insert into need_versions(id, need_id, version, goal, budget_vnd_session, duration_minutes,
                                          frequency, start_date)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """).params(versionId, needId, version, draft.goal(), draft.budgetVndSession(),
                        draft.durationMinutes(), draft.frequencyPer7Days(), draft.cycleStartDate())
                .update();
    }

    /** Edits the unconfirmed version in place; a confirmed one never matches. */
    @Transactional(propagation = Propagation.MANDATORY)
    void updateDraftVersion(UUID versionId, Draft draft) {
        int updated = jdbc.sql("""
                update need_versions
                set goal = ?, budget_vnd_session = ?, duration_minutes = ?, frequency = ?, start_date = ?
                where id = ? and confirmed_at is null
                """).params(draft.goal(), draft.budgetVndSession(), draft.durationMinutes(),
                        draft.frequencyPer7Days(), draft.cycleStartDate(), versionId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Only the unconfirmed version of a locked DRAFT need is edited");
        }
    }

    /** The need is DRAFT again, with {@code versionId} current and the next revision. */
    @Transactional(propagation = Propagation.MANDATORY)
    void markDraft(UUID needId, UUID versionId, long revision) {
        jdbc.sql("update learning_needs set status = 'DRAFT', current_version_id = ?, revision = ? where id = ?")
                .params(versionId, revision, needId).update();
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        Draft draft = new Draft(rs.getString("goal"), rs.getObject("budget_vnd_session", Integer.class),
                rs.getObject("duration_minutes", Integer.class), rs.getObject("frequency", Integer.class),
                rs.getObject("start_date", LocalDate.class));
        return new Row(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class), rs.getLong("revision"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("version_id", UUID.class), rs.getInt("version"), rs.getBoolean("confirmed"),
                rs.getObject("student_calendar_id", UUID.class), draft);
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
