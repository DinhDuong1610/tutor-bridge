package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V17 (TB-13, Đính 24/11/2026 on #13): a verification keeps when it was given to its verifier, so the
 * round robin follows real assignments, not submission times.
 */
class VerificationAssignedAtMigrationTest extends IntegrationTest {

    @Test
    void rows_assigned_before_v17_get_their_submission_time_and_unassigned_ones_none() throws SQLException {
        String database = "tb_assigned_" + UUID.randomUUID().toString().replace("-", "");
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

        migrate(url, "16");
        UUID verifier = UUID.randomUUID();
        UUID tutor = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        UUID assigned = UUID.randomUUID();
        UUID waiting = UUID.randomUUID();
        try (Connection migrator = migrator(url)) {
            var statement = migrator.createStatement();
            statement.execute("insert into accounts(id, role, status) values ('" + verifier + "', 'ADMIN', 'ACTIVE')");
            statement.execute("insert into accounts(id, role, status) values ('" + tutor + "', 'TUTOR', 'ACTIVE')");
            statement.execute("""
                    insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60)
                    values ('%s', '%s', 1, 'PENDING', '%s', 12, 180000)""".formatted(profile, tutor, "d".repeat(60)));
            for (UUID id : List.of(assigned, waiting)) {
                String to = id.equals(assigned) ? "'" + verifier + "'" : "null";
                statement.execute("""
                        insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to,
                                                        submitted_at)
                        values ('%s', '%s', 'MATH8_ALGEBRA', 'PENDING', '%s', %s, '2026-11-20T02:00:00Z')"""
                        .formatted(id, profile, "0".repeat(64), to));
            }
        }
        migrate(url, "latest");

        try (Connection migrator = migrator(url)) {
            ResultSet rows = migrator.createStatement().executeQuery("""
                    select id, assigned_at = submitted_at as backfilled, assigned_at is null as none
                    from tutor_verifications where id in ('%s', '%s')""".formatted(assigned, waiting));
            int seen = 0;
            while (rows.next()) {
                seen++;
                if (rows.getObject("id", UUID.class).equals(assigned)) {
                    assertThat(rows.getBoolean("backfilled")).as("assigned: submission time").isTrue();
                } else {
                    assertThat(rows.getBoolean("none")).as("nobody has it, so no time").isTrue();
                }
            }
            assertThat(seen).isEqualTo(2);
        }
    }

    @Test
    void the_app_records_the_time_with_the_verifier_and_never_a_time_without_one() {
        UUID record = verification(account("ADMIN"));

        jdbc.sql("update tutor_verifications set assigned_at = clock_timestamp() where id = ?").param(record).update();
        assertThat(jdbc.sql("select assigned_at is not null from tutor_verifications where id = ?").param(record)
                .query(Boolean.class).single()).isTrue();

        UUID unassigned = verification(null);
        assertThatThrownBy(() -> jdbc.sql("update tutor_verifications set assigned_at = now() where id = ?")
                .param(unassigned).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void code_on_v16_still_assigns_without_the_time() {
        UUID record = verification(null);

        jdbc.sql("update tutor_verifications set assigned_to = ? where id = ?").params(account("ADMIN"), record)
                .update();

        assertThat(jdbc.sql("select assigned_at is null from tutor_verifications where id = ?").param(record)
                .query(Boolean.class).single()).isTrue();
    }

    private UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into accounts(id, role, status) values (?, ?, 'ACTIVE')").params(id, role).update();
        return id;
    }

    private UUID verification(UUID assignedTo) {
        UUID profile = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60)
                values (?, ?, 1, 'PENDING', ?, 12, 180000)""").params(profile, account("TUTOR"), "d".repeat(60))
                .update();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to)
                values (?, ?, 'MATH8_ALGEBRA', 'PENDING', ?, ?)""").params(id, profile, "0".repeat(64), assignedTo)
                .update();
        return id;
    }

    private static Connection migrator(String url) throws SQLException {
        Connection connection = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD);
        connection.createStatement().execute("set search_path = tutorbridge, public");
        return connection;
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
