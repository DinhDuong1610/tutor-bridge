package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V16 (TB-13, Đính 23/11/2026 on #13): an ADMIN_MFA challenge counts its wrong codes, so it stops after
 * five, and an MFA enrollment remembers the last TOTP step it accepted, so a code is never taken twice.
 */
class AdminMfaColumnsMigrationTest extends IntegrationTest {

    private static final String DIGEST = "a".repeat(64);

    @Test
    void rows_from_before_v16_get_no_failures_and_no_used_step() throws SQLException {
        String database = "tb_mfa_" + UUID.randomUUID().toString().replace("-", "");
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

        migrate(url, "15");
        UUID account = UUID.randomUUID();
        UUID challenge = UUID.randomUUID();
        try (Connection migrator = migrator(url)) {
            var statement = migrator.createStatement();
            statement.execute("insert into accounts(id, role, status) values ('" + account + "', 'ADMIN', 'ACTIVE')");
            statement.execute("""
                    insert into auth_challenges(id, account_id, purpose, token_digest, expires_at)
                    values ('%s', '%s', 'ADMIN_MFA', '%s', now() + interval '5 minutes')
                    """.formatted(challenge, account, DIGEST));
            statement.execute("""
                    insert into admin_mfa_enrollments(account_id, secret_handle, enrolled_at)
                    values ('%s', 'v1:handle', now())""".formatted(account));
        }
        migrate(url, "latest");

        try (Connection migrator = migrator(url)) {
            ResultSet failures = migrator.createStatement().executeQuery(
                    "select failed_attempts from auth_challenges where id = '" + challenge + "'");
            assertThat(failures.next()).isTrue();
            assertThat(failures.getInt(1)).isZero();
            ResultSet step = migrator.createStatement().executeQuery(
                    "select last_used_step from admin_mfa_enrollments where account_id = '" + account + "'");
            assertThat(step.next()).isTrue();
            assertThat(step.getObject(1)).isNull();
        }
    }

    @Test
    void the_app_counts_wrong_codes_and_never_below_zero() {
        UUID challenge = challenge(admin());

        jdbc.sql("update auth_challenges set failed_attempts = failed_attempts + 1 where id = ?").param(challenge)
                .update();

        assertThat(jdbc.sql("select failed_attempts from auth_challenges where id = ?").param(challenge)
                .query(Integer.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.sql("update auth_challenges set failed_attempts = -1 where id = ?")
                .param(challenge).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void the_app_records_the_last_used_step_and_only_a_positive_one() {
        UUID account = admin();
        jdbc.sql("insert into admin_mfa_enrollments(account_id, secret_handle, enrolled_at) values (?, ?, ?)")
                .params(account, "v1:" + UUID.randomUUID(), java.sql.Timestamp.from(Instant.now())).update();
        long step = Instant.now().getEpochSecond() / 30;

        jdbc.sql("update admin_mfa_enrollments set last_used_step = ? where account_id = ?").params(step, account)
                .update();

        assertThat(jdbc.sql("select last_used_step from admin_mfa_enrollments where account_id = ?").param(account)
                .query(Long.class).single()).isEqualTo(step);
        assertThatThrownBy(() -> jdbc.sql("update admin_mfa_enrollments set last_used_step = 0 where account_id = ?")
                .param(account).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID admin() {
        UUID account = UUID.randomUUID();
        jdbc.sql("insert into accounts(id, role, status) values (?, 'ADMIN', 'ACTIVE')").param(account).update();
        return account;
    }

    private UUID challenge(UUID account) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into auth_challenges(id, account_id, purpose, token_digest, expires_at)
                values (?, ?, 'ADMIN_MFA', ?, now() + interval '5 minutes')""")
                .params(id, account, UUID.randomUUID().toString().replace("-", "").repeat(2)).update();
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
