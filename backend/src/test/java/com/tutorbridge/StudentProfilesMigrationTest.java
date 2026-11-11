package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** V10 (TB-11): student creation time for listStudents, and the goal history of UC02 step 3. */
class StudentProfilesMigrationTest extends IntegrationTest {

    @Test
    void students_from_before_v10_get_a_creation_time_and_their_goal_as_first_history_row() throws SQLException {
        String database = "tb_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(
                PostgresTestSupport.POSTGRES.getJdbcUrl(),
                PostgresTestSupport.POSTGRES.getUsername(),
                PostgresTestSupport.POSTGRES.getPassword())) {
            admin.createStatement().execute("create database " + database + " owner tutorbridge_migrator");
        }
        String url = PostgresTestSupport.POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        try (Connection admin = DriverManager.getConnection(url,
                PostgresTestSupport.POSTGRES.getUsername(), PostgresTestSupport.POSTGRES.getPassword())) {
            admin.createStatement().execute("create extension if not exists vector");
        }

        migrate(url, "9");
        UUID parent = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        try (Connection migrator = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)) {
            var statement = migrator.createStatement();
            statement.execute("set search_path = tutorbridge, public");
            statement.execute("insert into accounts(id, role, status) values ('" + parent + "', 'PARENT', 'ACTIVE')");
            statement.execute("insert into students(id, parent_id, display_name, revision, short_goal) values ('"
                    + student + "', '" + parent + "', 'Hoc sinh thu', 3, 'Muc tieu thu')");
        }
        migrate(url, "latest");

        try (Connection migrator = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)) {
            var created = migrator.createStatement().executeQuery(
                    "select created_at from tutorbridge.students where id = '" + student + "'");
            assertThat(created.next()).isTrue();
            assertThat(created.getTimestamp(1)).isNotNull();

            var history = migrator.createStatement().executeQuery("""
                    select revision, short_goal, changed_by from tutorbridge.student_goal_versions
                    where student_id = '%s'
                    """.formatted(student));
            assertThat(history.next()).isTrue();
            assertThat(history.getLong(1)).isEqualTo(3);
            assertThat(history.getString(2)).isEqualTo("Muc tieu thu");
            assertThat(history.getObject(3, UUID.class)).isEqualTo(parent);
            assertThat(history.next()).isFalse();
        }
    }

    @Test
    void UC02_application_role_records_one_goal_row_per_revision() {
        UUID parent = newAccount();
        UUID student = newStudent(parent);
        insertGoal(student, 1, "Muc tieu dau", parent);
        insertGoal(student, 2, "Muc tieu sau", parent);

        Integer rows = jdbc.sql("select count(*) from student_goal_versions where student_id = ?")
                .param(student).query(Integer.class).single();
        assertThat(rows).isEqualTo(2);
        assertThatThrownBy(() -> insertGoal(student, 2, "Trung phien ban", parent))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void goal_rows_need_an_existing_student_account_and_a_positive_revision() {
        UUID parent = newAccount();
        UUID student = newStudent(parent);

        assertThatThrownBy(() -> insertGoal(UUID.randomUUID(), 1, "Khong co hoc sinh", parent))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGoal(student, 1, "Khong co tai khoan", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGoal(student, 0, "Phien ban 0", parent))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGoal(student, 1, "x".repeat(2001), parent))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void new_students_get_a_creation_time_without_naming_the_column() {
        UUID student = newStudent(newAccount());

        Boolean hasTime = jdbc.sql("select created_at is not null from students where id = ?")
                .param(student).query(Boolean.class).single();
        assertThat(hasTime).isTrue();
    }

    private UUID newStudent(UUID parent) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into students(id, parent_id, display_name) values (?, ?, 'Hoc sinh thu')")
                .param(id).param(parent).update();
        return id;
    }

    private void insertGoal(UUID student, long revision, String goal, UUID changedBy) {
        jdbc.sql("""
                insert into student_goal_versions(student_id, revision, short_goal, changed_by)
                values (?, ?, ?, ?)
                """).param(student).param(revision).param(goal).param(changedBy).update();
    }

    private static void migrate(String url, String target) {
        Flyway.configure()
                .dataSource(url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)
                .defaultSchema("public")
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }
}
