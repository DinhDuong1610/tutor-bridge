package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * V15 (TB-17, #17 Q3) on a database that already holds matching runs: the runs and candidates stay as
 * they were, and a stored candidate can take its common schedule afterwards. Rules of the new table are
 * in {@code classes.MatchingCandidateSessionsMigrationTest}.
 */
class MatchingCandidateSessionsUpgradeTest extends IntegrationTest {

    @Test
    void runs_from_before_v15_are_kept_and_their_candidates_can_take_sessions() throws SQLException {
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

        migrate(url, "12");
        UUID run = UUID.randomUUID();
        UUID tutor = UUID.randomUUID();
        try (Connection migrator = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)) {
            Statement statement = migrator.createStatement();
            statement.execute("set search_path = tutorbridge, public");
            UUID parent = UUID.randomUUID();
            UUID student = UUID.randomUUID();
            UUID studentCalendar = UUID.randomUUID();
            UUID tutorCalendar = UUID.randomUUID();
            UUID profile = UUID.randomUUID();
            UUID need = UUID.randomUUID();
            UUID version = UUID.randomUUID();
            statement.execute("insert into accounts(id, role, status) values ('%s', 'PARENT', 'ACTIVE'), ('%s', 'TUTOR', 'ACTIVE')"
                    .formatted(parent, tutor));
            statement.execute("insert into students(id, parent_id, display_name) values ('%s', '%s', 'Hoc sinh thu')"
                    .formatted(student, parent));
            statement.execute("""
                    insert into calendars(id, owner_kind, owner_id, version, effective)
                    values ('%s', 'STUDENT', '%s', 1, true), ('%s', 'TUTOR', '%s', 1, true)"""
                    .formatted(studentCalendar, student, tutorCalendar, tutor));
            statement.execute("""
                    insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                               current)
                    values ('%s', '%s', 1, 'APPROVED', repeat('x', 50), 24, 100000, true)""".formatted(profile, tutor));
            statement.execute("begin");
            statement.execute("insert into learning_needs(id, student_id, status) values ('%s', '%s', 'CONFIRMED')"
                    .formatted(need, student));
            statement.execute("""
                    insert into need_versions(id, need_id, version, goal, budget_vnd_session, duration_minutes, frequency,
                                              start_date, confirmed_at, student_calendar_id)
                    values ('%s', '%s', 1, repeat('Muc tieu ', 5), 200000, 90, 1, current_date + 14, now(), '%s')"""
                    .formatted(version, need, studentCalendar));
            statement.execute("update learning_needs set current_version_id = '%s' where id = '%s'".formatted(version, need));
            statement.execute("commit");
            statement.execute("""
                    insert into matching_runs(id, need_version_id, mode, algorithm_version, config_version, status, expires_at)
                    values ('%s', '%s', 'B', 'B-v1', 'none', 'COMPLETED', now() + interval '24 hours')"""
                    .formatted(run, version));
            statement.execute("""
                    insert into matching_candidates(run_id, tutor_id, profile_id, tutor_calendar_id, rank, s, e, a, score,
                                                    quoted_price, hard_filters_passed)
                    values ('%s', '%s', '%s', '%s', 1, null, 0.4, 0.5, 0.4375, 150000, true)"""
                    .formatted(run, tutor, profile, tutorCalendar));
        }

        migrate(url, "latest");

        try (Connection migrator = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)) {
            Statement statement = migrator.createStatement();
            statement.execute("set search_path = tutorbridge, public");
            var kept = statement.executeQuery("select rank, quoted_price from matching_candidates where run_id = '" + run + "'");
            assertThat(kept.next()).isTrue();
            assertThat(kept.getInt(1)).isEqualTo(1);
            assertThat(kept.getInt(2)).isEqualTo(150000);
            statement.execute("""
                    insert into matching_candidate_sessions(run_id, tutor_id, ordinal, during)
                    values ('%s', '%s', 1, tstzrange(now() + interval '15 days', now() + interval '15 days 90 minutes', '[)'))"""
                    .formatted(run, tutor));
            var sessions = statement.executeQuery(
                    "select count(*) from matching_candidate_sessions where run_id = '" + run + "'");
            assertThat(sessions.next()).isTrue();
            assertThat(sessions.getInt(1)).isEqualTo(1);
        }
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
