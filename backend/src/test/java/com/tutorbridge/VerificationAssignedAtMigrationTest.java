package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V17 (TB-13, Đính 24/11/2026 on #13): a verification keeps when it was given to its verifier, so the
 * round robin follows real assignments, not submission times.
 */
class VerificationAssignedAtMigrationTest extends IntegrationTest {

    /**
     * Upgrading a V16 database whose history the guard of V11 would refuse today (Codex and Huy on #223): a
     * verification of a RETIRED version, and a VERIFIED one whose reviewer lost VERIFY afterwards. Each row
     * is written in an order V16 accepts. After the upgrade the guard is on again and still closes them.
     */
    @Test
    void rows_assigned_before_v17_get_their_submission_time_even_when_the_guard_would_refuse_them_today()
            throws SQLException {
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
        UUID assigned = UUID.randomUUID();
        UUID waiting = UUID.randomUUID();
        UUID retired = UUID.randomUUID();
        UUID verified = UUID.randomUUID();
        try (Connection migrator = migrator(url)) {
            Statement statement = migrator.createStatement();
            statement.execute("insert into accounts(id, role, status) values ('" + verifier + "', 'ADMIN', 'ACTIVE')");
            statement.execute("""
                    insert into admin_assignments(account_id, capability, granted_by) values ('%s', 'VERIFY', '%s')"""
                    .formatted(verifier, verifier));
            UUID pending = version(statement, "PENDING");
            insert(statement, assigned, pending, "PENDING", verifier, null);
            insert(statement, waiting, pending, "PENDING", null, null);
            // OP-24: asked for more information, then replaced by a newer version.
            UUID replaced = version(statement, "PENDING");
            insert(statement, retired, replaced, "PENDING", verifier, "Thiếu bằng cấp.");
            statement.execute("update tutor_profiles set status = 'RETIRED' where id = '" + replaced + "'");
            // Verified while the reviewer held VERIFY, which was revoked later.
            insert(statement, verified, version(statement, "APPROVED"), "VERIFIED", verifier, "Đủ minh chứng.");
            statement.execute("update admin_assignments set revoked_at = now() where account_id = '" + verifier + "'");
        }
        migrate(url, "latest");

        try (Connection migrator = migrator(url)) {
            ResultSet rows = migrator.createStatement().executeQuery("""
                    select id, assigned_at = submitted_at as backfilled, assigned_at is null as none
                    from tutor_verifications where id in ('%s', '%s', '%s', '%s')"""
                    .formatted(assigned, waiting, retired, verified));
            int seen = 0;
            while (rows.next()) {
                seen++;
                if (rows.getObject("id", UUID.class).equals(waiting)) {
                    assertThat(rows.getBoolean("none")).as("nobody has it, so no time").isTrue();
                } else {
                    assertThat(rows.getBoolean("backfilled")).as(rows.getString("id")).isTrue();
                }
            }
            assertThat(seen).isEqualTo(4);

            ResultSet trigger = migrator.createStatement().executeQuery(
                    "select tgenabled from pg_trigger where tgname = 'verification_guard'");
            assertThat(trigger.next()).isTrue();
            assertThat(trigger.getString(1)).as("the guard is on again").isEqualTo("O");
            Statement later = migrator.createStatement();
            assertThatThrownBy(() -> later.execute(
                    "update tutor_verifications set reason = 'Muộn.' where id = '" + retired + "'"))
                    .hasMessageContaining("retired profile version is closed");
            assertThatThrownBy(() -> later.execute(
                    "update tutor_verifications set scope = 'MATH8_ALGEBRA' where id = '" + verified + "'"))
                    .hasMessageContaining("assigned independent reviewer");
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

    private static UUID version(Statement statement, String status) throws SQLException {
        UUID tutor = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        statement.execute("insert into accounts(id, role, status) values ('" + tutor + "', 'TUTOR', 'ACTIVE')");
        statement.execute("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60)
                values ('%s', '%s', 1, '%s', '%s', 12, 180000)""".formatted(id, tutor, status, "d".repeat(60)));
        return id;
    }

    /** A record as V16 code or a verifier left it; a reason means {@code verifier} decided it. */
    private static void insert(Statement statement, UUID id, UUID profile, String status, UUID verifier,
            String reason) throws SQLException {
        String to = verifier == null ? "null" : "'" + verifier + "'";
        String decided = reason == null ? "null, null, null" : "'" + reason + "', " + to + ", now()";
        statement.execute("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to, reason,
                                                reviewed_by, reviewed_at, submitted_at)
                values ('%s', '%s', 'MATH8_ALGEBRA', '%s', '%s', %s, %s, '2026-11-20T02:00:00Z')"""
                .formatted(id, profile, status, "0".repeat(64), to, decided));
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
