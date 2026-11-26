package com.tutorbridge.classes.internal;

import com.tutorbridge.platform.api.Cursor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads of {@code classes}, their effective goal and their {@code lesson_sessions} (TB-20). */
@Repository
class ClassRepository {

    /**
     * One class with its effective goal. A left join, so a class whose goal is not EFFECTIVE is still found
     * and the service can tell that broken invariant apart from a class the caller may not read.
     */
    record Row(UUID id, UUID studentId, UUID tutorId, long revision, String state, int priceVndSession,
            int durationMinutes, UUID effectiveGoalVersionId, String goal, Instant createdAt) {}

    private static final String SELECT = """
            select c.id, c.student_id, c.tutor_id, c.revision, c.status as state, c.price_vnd_session,
                   c.duration_minutes, g.id as effective_goal_version_id, g.goal, c.created_at
            from classes c
            left join class_goal_versions g on g.class_id = c.id and g.status = 'EFFECTIVE'
            """;

    record SessionRow(UUID id, UUID classId, int ordinal, long revision, String state, Instant start, Instant end) {}

    private final JdbcClient jdbc;

    ClassRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Row> find(UUID classId) {
        return jdbc.sql(SELECT + "where c.id = ?").param(classId).query(Row.class).optional();
    }

    /** A tutor's classes newest first by the keyset (created_at, id), through V20 classes_tutor_order. */
    List<Row> listByTutor(UUID tutorId, Cursor after, int limit) {
        return list("c.tutor_id = :owner", tutorId, after, limit);
    }

    /** The classes of these students newest first, through V20 classes_student_order. */
    List<Row> listByStudents(Collection<UUID> studentIds, Cursor after, int limit) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return list("c.student_id = any(:owner)", studentIds.toArray(UUID[]::new), after, limit);
    }

    private List<Row> list(String owner, Object ownerValue, Cursor after, int limit) {
        return jdbc.sql(SELECT + """
                where %s
                  and (cast(:afterAt as timestamptz) is null
                       or (c.created_at, c.id) < (cast(:afterAt as timestamptz), cast(:afterId as uuid)))
                order by c.created_at desc, c.id desc
                limit :limit
                """.formatted(owner))
                .param("owner", ownerValue)
                .param("afterAt", after == null ? null : Timestamp.from(after.at()))
                .param("afterId", after == null ? null : after.id())
                .param("limit", limit)
                .query(Row.class).list();
    }

    /**
     * For each of these classes, its earliest SCHEDULED session that has not ended at {@code now} (OP-27),
     * in one query for the whole page; a class without one has no entry. Through UNIQUE(class_id, ordinal).
     */
    Map<UUID, SessionRow> nextSessions(Collection<UUID> classIds, Instant now) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SessionRow> next = new HashMap<>();
        jdbc.sql("""
                select distinct on (class_id)
                       id, class_id, ordinal, revision, state, lower(during) as start, upper(during) as "end"
                from lesson_sessions
                where class_id = any(:classes) and state = 'SCHEDULED' and upper(during) > :now
                order by class_id, lower(during), id
                """)
                .param("classes", classIds.toArray(UUID[]::new))
                .param("now", Timestamp.from(now))
                .query(SessionRow.class).list()
                .forEach(row -> next.put(row.classId(), row));
        return next;
    }

    /** The sessions of one class by start time, then id, after the cursor; through UNIQUE(class_id, ordinal). */
    List<SessionRow> sessions(UUID classId, Cursor after, int limit) {
        return jdbc.sql("""
                select id, class_id, ordinal, revision, state, lower(during) as start, upper(during) as "end"
                from lesson_sessions
                where class_id = :classId
                  and (cast(:afterAt as timestamptz) is null
                       or (lower(during), id) > (cast(:afterAt as timestamptz), cast(:afterId as uuid)))
                order by lower(during), id
                limit :limit
                """)
                .param("classId", classId)
                .param("afterAt", after == null ? null : Timestamp.from(after.at()))
                .param("afterId", after == null ? null : after.id())
                .param("limit", limit)
                .query(SessionRow.class).list();
    }
}
