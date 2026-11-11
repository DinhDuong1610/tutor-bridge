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

/** V9 (TB-08): session device label, terms acceptance, login failures and attempt counters. */
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
                values ('RECOVERY', 'ACCOUNT', ?, now(), 3, now() + interval '1 hour')
                """).param(DIGEST).update();
        jdbc.sql("insert into auth_login_failures(scope, subject_digest, failed_at) values ('SOURCE', ?, now())")
                .param(DIGEST).update();

        int updated = jdbc.sql("""
                update auth_rate_counters set attempt_count = attempt_count + 1
                where action = 'RECOVERY' and scope = 'ACCOUNT' and subject_digest = ?
                """).param(DIGEST).update();
        int deleted = jdbc.sql("delete from auth_rate_counters where subject_digest = ?").param(DIGEST).update();
        int failuresDeleted = jdbc.sql("delete from auth_login_failures where subject_digest = ?").param(DIGEST).update();

        assertThat(updated).isEqualTo(1);
        assertThat(deleted).isEqualTo(1);
        assertThat(failuresDeleted).isEqualTo(1);
    }

    @Test
    void UC01_login_failures_are_counted_over_the_last_15_minutes_across_any_window_edge() {
        String subject = randomDigest();
        OffsetDateTime now = OffsetDateTime.now();
        // Four failures just before and four just after a 15 minute edge: a fixed window would see 4 and 4.
        for (int second = 0; second < 8; second++) {
            recordLoginFailure(subject, now.minusSeconds(60 - second * 8L));
        }
        // Older than 15 minutes: outside the sliding window.
        recordLoginFailure(subject, now.minusMinutes(16));

        Integer recent = jdbc.sql("""
                select count(*) from auth_login_failures
                where scope = 'ACCOUNT' and subject_digest = ? and failed_at > ? - interval '15 minutes'
                """).param(subject).param(now).query(Integer.class).single();

        assertThat(recent).isEqualTo(8);
    }

    @Test
    void login_failures_reject_raw_subjects_and_unknown_scopes() {
        OffsetDateTime now = OffsetDateTime.now();
        for (String[] row : new String[][] {
            {"ACCOUNT", "parent@example.invalid"},
            {"ACCOUNT", "A".repeat(64)},
            {"DEVICE", DIGEST},
        }) {
            assertThatThrownBy(() -> jdbc.sql(
                            "insert into auth_login_failures(scope, subject_digest, failed_at) values (?, ?, ?)")
                            .param(row[0]).param(row[1]).param(now).update())
                    .as(String.join(",", row))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
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
            {"RECOVERY", "ACCOUNT", "parent@example.invalid"},
            {"RECOVERY", "ACCOUNT", "A".repeat(64)},
            {"PAYMENT", "ACCOUNT", DIGEST},
            {"RECOVERY", "DEVICE", DIGEST},
            // LOGIN is counted per failure in auth_login_failures, never in a fixed window.
            {"LOGIN", "ACCOUNT", DIGEST},
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

    private void recordLoginFailure(String subject, OffsetDateTime at) {
        jdbc.sql("insert into auth_login_failures(scope, subject_digest, failed_at) values ('ACCOUNT', ?, ?)")
                .param(subject).param(at).update();
    }

    private static String randomDigest() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
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
