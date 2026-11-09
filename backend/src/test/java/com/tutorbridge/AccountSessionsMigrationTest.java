package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** V9 (TB-08): session device label, terms acceptance and attempt counters. */
class AccountSessionsMigrationTest extends IntegrationTest {

    private static final String DIGEST = "a".repeat(64);

    @Test
    void sessions_issued_before_v9_get_an_empty_device_label() throws SQLException {
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

        migrate(url, "8");
        UUID account = UUID.randomUUID();
        try (Connection migrator = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)) {
            var statement = migrator.createStatement();
            statement.execute("set search_path = tutorbridge, public");
            statement.execute("insert into accounts(id, role, status) values ('" + account + "', 'PARENT', 'ACTIVE')");
            statement.execute("insert into auth_sessions(id, account_id, token_digest, expires_at) values ('"
                    + UUID.randomUUID() + "', '" + account + "', 'digest-before-v9', now() + interval '1 day')");
        }
        migrate(url, "latest");

        try (Connection migrator = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)) {
            var rows = migrator.createStatement().executeQuery(
                    "select device_label from tutorbridge.auth_sessions where account_id = '" + account + "'");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEmpty();
        }
    }

    @Test
    void application_role_records_device_terms_and_counters() {
        UUID account = newAccount();
        jdbc.sql("""
                insert into auth_sessions(id, account_id, token_digest, expires_at, device_label)
                values (?, ?, ?, now() + interval '1 day', 'Android 15')
                """).param(UUID.randomUUID()).param(account).param(UUID.randomUUID().toString()).update();
        jdbc.sql("insert into terms_acceptances(account_id, terms_version) values (?, 'thu-nghiem-1')")
                .param(account).update();
        jdbc.sql("""
                insert into auth_rate_counters(action, scope, subject_digest, window_started_at, attempt_count, blocked_until)
                values ('LOGIN', 'ACCOUNT', ?, now(), 5, now() + interval '15 minutes')
                """).param(DIGEST).update();

        int updated = jdbc.sql("""
                update auth_rate_counters set attempt_count = attempt_count + 1
                where action = 'LOGIN' and scope = 'ACCOUNT' and subject_digest = ?
                """).param(DIGEST).update();
        int deleted = jdbc.sql("delete from auth_rate_counters where subject_digest = ?").param(DIGEST).update();

        assertThat(updated).isEqualTo(1);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void terms_are_accepted_once_per_version() {
        UUID account = newAccount();
        jdbc.sql("insert into terms_acceptances(account_id, terms_version) values (?, 'thu-nghiem-1')")
                .param(account).update();

        assertThatThrownBy(() -> jdbc.sql("insert into terms_acceptances(account_id, terms_version) values (?, 'thu-nghiem-1')")
                        .param(account).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("insert into terms_acceptances(account_id, terms_version) values (?, '')")
                        .param(account).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void counters_reject_raw_subjects_and_unknown_actions() {
        OffsetDateTime now = OffsetDateTime.now();
        for (String[] row : new String[][] {
            {"LOGIN", "ACCOUNT", "parent@example.invalid"},
            {"LOGIN", "ACCOUNT", "A".repeat(64)},
            {"PAYMENT", "ACCOUNT", DIGEST},
            {"LOGIN", "DEVICE", DIGEST},
        }) {
            assertThatThrownBy(() -> jdbc.sql("""
                            insert into auth_rate_counters(action, scope, subject_digest, window_started_at, attempt_count)
                            values (?, ?, ?, ?, 1)
                            """).param(row[0]).param(row[1]).param(row[2]).param(now).update())
                    .as(String.join(",", row))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void counters_need_a_positive_count_and_a_block_after_the_window_start() {
        OffsetDateTime now = OffsetDateTime.now();
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into auth_rate_counters(action, scope, subject_digest, window_started_at, attempt_count)
                        values ('RECOVERY', 'SOURCE', ?, ?, 0)
                        """).param(DIGEST).param(now).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                        insert into auth_rate_counters(action, scope, subject_digest, window_started_at, attempt_count, blocked_until)
                        values ('RECOVERY', 'SOURCE', ?, ?, 1, ?)
                        """).param(DIGEST).param(now).param(now.minusMinutes(1)).update())
                .isInstanceOf(DataIntegrityViolationException.class);
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
