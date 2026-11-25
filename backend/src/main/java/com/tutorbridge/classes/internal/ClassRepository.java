package com.tutorbridge.classes.internal;

import com.tutorbridge.platform.api.Cursor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads of {@code classes}, their effective goal and their {@code lesson_sessions} (TB-20). */
@Repository
class ClassRepository {

    /** One class with its effective goal, when it has one (#19 Q1 may make the first goal wait). */
    record Row(UUID id, UUID studentId, UUID tutorId, long revision, String state, int priceVndSession,
            int durationMinutes, UUID effectiveGoalVersionId, String goal) {}

    record SessionRow(UUID id, UUID classId, int ordinal, long revision, String state, Instant start, Instant end) {}

    private final JdbcClient jdbc;

    ClassRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Row> find(UUID classId) {
        return jdbc.sql("""
                select c.id, c.student_id, c.tutor_id, c.revision, c.status as state, c.price_vnd_session,
                       c.duration_minutes, g.id as effective_goal_version_id, g.goal
                from classes c
                left join class_goal_versions g on g.class_id = c.id and g.status = 'EFFECTIVE'
                where c.id = ?
                """).param(classId).query(Row.class).optional();
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
