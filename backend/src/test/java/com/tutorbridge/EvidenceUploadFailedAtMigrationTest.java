package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V21 (TB-182, Đính on #262, 28/11/2026): an evidence upload keeps when it became FAILED, so cleanup counts
 * its seven days from the failure, not from the ticket.
 */
class EvidenceUploadFailedAtMigrationTest extends IntegrationTest {

    /** Uploads failed before V21 are counted from the upgrade; no other state gets a time. */
    @Test
    void uploads_failed_before_v21_are_counted_from_the_upgrade() throws SQLException {
        String database = "tb_failed_at_" + UUID.randomUUID().toString().replace("-", "");
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

        // The last version before V21 on dev: V20 (#258).
        migrate(url, "20");
        Map<UUID, String> states = new HashMap<>();
        try (Connection migrator = migrator(url)) {
            Statement statement = migrator.createStatement();
            UUID tutor = UUID.randomUUID();
            statement.execute("insert into accounts(id, role, status) values ('" + tutor + "', 'TUTOR', 'ACTIVE')");
            for (String state : new String[] {"UPLOADING", "QUARANTINED", "READY", "FAILED", "DELETED"}) {
                UUID id = UUID.randomUUID();
                states.put(id, state);
                // A ticket that expired 30 days ago: the age V20 code would have counted from.
                statement.execute("""
                        insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256,
                                                           state, created_at, expires_at)
                        values ('%s', '%s', '%s', 'application/pdf', 10, '%s', '%s',
                                now() - interval '31 days', now() - interval '30 days')"""
                        .formatted(id, tutor, "evidence/" + id, "0".repeat(64), state));
            }
        }
        migrate(url, "latest");

        try (Connection migrator = migrator(url)) {
            ResultSet rows = migrator.createStatement().executeQuery("""
                    select id, failed_at is null as none, failed_at > now() - interval '1 hour' as recent
                    from tutor_evidence_uploads""");
            int seen = 0;
            while (rows.next()) {
                seen++;
                String state = states.get(rows.getObject("id", UUID.class));
                if (state.equals("FAILED")) {
                    assertThat(rows.getBoolean("recent")).as("counted from the upgrade, not the ticket").isTrue();
                } else {
                    assertThat(rows.getBoolean("none")).as(state).isTrue();
                }
            }
            assertThat(seen).isEqualTo(5);
        }
    }

    @Test
    void a_failure_time_belongs_to_a_failed_or_deleted_upload_only() {
        for (String state : new String[] {"UPLOADING", "QUARANTINED", "READY"}) {
            UUID upload = upload(state);
            assertThatThrownBy(() -> jdbc.sql("update tutor_evidence_uploads set failed_at = now() where id = ?")
                    .param(upload).update()).as(state).isInstanceOf(DataIntegrityViolationException.class);
        }

        UUID failed = upload("QUARANTINED");
        jdbc.sql("update tutor_evidence_uploads set state = 'FAILED', failed_at = now() where id = ?").param(failed)
                .update();
        // Cleanup moves it on to DELETED and the time stays.
        jdbc.sql("update tutor_evidence_uploads set state = 'DELETED' where id = ?").param(failed).update();
        assertThat(jdbc.sql("select failed_at is not null from tutor_evidence_uploads where id = ?").param(failed)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void code_on_v20_still_fails_an_upload_without_the_time() {
        UUID upload = upload("QUARANTINED");

        jdbc.sql("update tutor_evidence_uploads set state = 'FAILED' where id = ? and state = 'QUARANTINED'")
                .param(upload).update();

        assertThat(jdbc.sql("select failed_at is null from tutor_evidence_uploads where id = ?").param(upload)
                .query(Boolean.class).single()).isTrue();
    }

    private UUID upload(String state) {
        UUID tutor = UUID.randomUUID();
        jdbc.sql("insert into accounts(id, role, status) values (?, 'TUTOR', 'ACTIVE')").param(tutor).update();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at)
                values (?, ?, ?, 'application/pdf', 10, ?, ?, now() + interval '15 minutes')""")
                .params(id, tutor, "evidence/" + id, "0".repeat(64), state).update();
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
