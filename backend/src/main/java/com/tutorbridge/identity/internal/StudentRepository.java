package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Cursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code students} and {@code student_goal_versions} (V1, V5, V10). Callers check the owner. */
@Repository
class StudentRepository {

    record Row(UUID id, UUID parentId, String displayName, int grade, long revision, String shortGoal,
            Instant createdAt) {}

    private static final String COLUMNS = "id, parent_id, display_name, grade, revision, short_goal, created_at";

    private final JdbcClient jdbc;

    StudentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** True when the account is an active parent, held against change until the transaction ends. */
    boolean lockActiveParent(UUID accountId) {
        return jdbc.sql("select 1 from accounts where id = ? and role = 'PARENT' and status = 'ACTIVE' for share")
                .param(accountId).query((rs, n) -> 1).optional().isPresent();
    }

    void insert(UUID id, UUID parentId, String displayName, int grade, String shortGoal, Instant now) {
        jdbc.sql("""
                insert into students(id, parent_id, display_name, grade, short_goal, revision, created_at)
                values (?, ?, ?, ?, ?, 1, ?)
                """).params(id, parentId, displayName, grade, shortGoal, at(now)).update();
    }

    Optional<Row> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from students where id = ?")
                .param(id).query(StudentRepository::row).optional();
    }

    Optional<Row> lock(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from students where id = ? for update")
                .param(id).query(StudentRepository::row).optional();
    }

    /** Oldest first, so children stay in the order the parent added them. */
    List<Row> listByParent(UUID parentId, Cursor after, int limit) {
        String position = after == null ? "" : "and (created_at, id) > (:afterAt, :afterId)";
        var query = jdbc.sql("""
                select %s from students
                where parent_id = :parent %s
                order by created_at, id
                limit :limit
                """.formatted(COLUMNS, position)).param("parent", parentId).param("limit", limit);
        if (after != null) {
            query = query.param("afterAt", at(after.at())).param("afterId", after.id());
        }
        return query.query(StudentRepository::row).list();
    }

    void update(UUID id, String displayName, int grade, String shortGoal, long revision) {
        jdbc.sql("""
                update students set display_name = ?, grade = ?, short_goal = ?, revision = ?
                where id = ?
                """).params(displayName, grade, shortGoal, revision, id).update();
    }

    /** UC02 step 3: one row each time the goal takes a value; never updated. */
    void insertGoal(UUID studentId, long revision, String shortGoal, UUID changedBy, Instant now) {
        jdbc.sql("""
                insert into student_goal_versions(student_id, revision, short_goal, changed_by, changed_at)
                values (?, ?, ?, ?, ?)
                """).params(studentId, revision, shortGoal, changedBy, at(now)).update();
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        return new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getInt(4),
                rs.getLong(5), rs.getString(6), rs.getObject(7, OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
