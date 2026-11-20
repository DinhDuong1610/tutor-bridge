package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** V12 (#98, OP-28): when a profile version was sent for verification, for the AD03 queue. */
class VerificationSubmittedAtMigrationTest extends IntegrationTest {

    @Test
    void a_verification_from_before_v12_gets_a_submission_time() throws SQLException {
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

        migrate(url, "11");
        UUID verification = UUID.randomUUID();
        try (Connection migrator = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)) {
            var statement = migrator.createStatement();
            statement.execute("set search_path = tutorbridge, public");
            UUID tutor = UUID.randomUUID();
            UUID profile = UUID.randomUUID();
            statement.execute("insert into accounts(id, role, status) values ('" + tutor + "', 'TUTOR', 'ACTIVE')");
            statement.execute("""
                    insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60)
                    values ('%s', '%s', 1, 'PENDING', '%s', 12, 180000)""".formatted(profile, tutor, "Mo ta ".repeat(10)));
            statement.execute("""
                    insert into tutor_verifications(id, profile_id, scope, status, evidence_key)
                    values ('%s', '%s', 'MATH8_ALGEBRA', 'PENDING', 'k')""".formatted(verification, profile));
        }
        migrate(url, "latest");

        try (Connection migrator = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)) {
            var submitted = migrator.createStatement().executeQuery(
                    "select submitted_at from tutorbridge.tutor_verifications where id = '" + verification + "'");
            assertThat(submitted.next()).isTrue();
            assertThat(submitted.getObject(1, OffsetDateTime.class)).isNotNull();
        }
    }

    @Test
    void OP_28_code_written_before_v12_still_inserts_and_gets_the_insert_time() {
        UUID tutor = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        UUID verification = UUID.randomUUID();
        jdbc.sql("insert into accounts(id, role, status) values (?, 'TUTOR', 'ACTIVE')").param(tutor).update();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60)
                values (?, ?, 1, 'PENDING', ?, 12, 180000)""").params(profile, tutor, "Mo ta ".repeat(10)).update();
        OffsetDateTime before = jdbc.sql("select now()").query(OffsetDateTime.class).single();

        // The insert of TutorVerificationRepository on dev, which does not name submitted_at.
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, revision)
                values (?, ?, 'MATH8_ALGEBRA', 'PENDING', 'k', 1)""").params(verification, profile).update();

        OffsetDateTime submitted = jdbc.sql("select submitted_at from tutor_verifications where id = ?")
                .param(verification).query(OffsetDateTime.class).single();
        assertThat(submitted).isAfterOrEqualTo(before);
    }

    @Test
    void the_queue_of_one_verifier_is_read_through_an_index() {
        String definition = jdbc.sql("select indexdef from pg_indexes where indexname = 'tutor_verifications_assigned_queue'")
                .query(String.class).single();
        assertThat(definition).contains("(assigned_to, submitted_at, id)");
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
