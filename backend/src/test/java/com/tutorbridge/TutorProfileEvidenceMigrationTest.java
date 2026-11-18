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
import org.springframework.dao.DuplicateKeyException;

/**
 * V11 (TB-12c): the evidence a profile version is submitted with (#12 Q3), and OP-24's guard that a
 * verification of a RETIRED version never changes. Every rule is tried straight against the database,
 * as the application role, next to a case it lets through.
 */
class TutorProfileEvidenceMigrationTest extends IntegrationTest {

    private static final String SHA = "0".repeat(64);

    // ---------------------------------------------------------------- fixtures

    private UUID tutor() {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into accounts(id, role, status) values (?, 'TUTOR', 'ACTIVE')").param(id).update();
        return id;
    }

    private UUID profile(UUID tutor, int version, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60)
                values (?, ?, ?, ?, ?, 12, 180000)
                """).params(id, tutor, version, status, "Mo ta cach day ".repeat(5)).update();
        return id;
    }

    private UUID upload(UUID owner, String state) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_evidence_uploads(id, owner_id, object_key, media_type, size_bytes, sha256, state,
                                                   expires_at)
                values (?, ?, ?, 'application/pdf', 100, ?, ?, now() + interval '15 minutes')
                """).params(id, owner, UUID.randomUUID().toString(), SHA, state).update();
        return id;
    }

    private void link(UUID profile, UUID upload) {
        jdbc.sql("insert into tutor_profile_evidence(profile_id, upload_id) values (?, ?)")
                .params(profile, upload).update();
    }

    private int links(UUID profile) {
        return jdbc.sql("select count(*) from tutor_profile_evidence where profile_id = ?").param(profile)
                .query(Integer.class).single();
    }

    private void moveProfile(UUID profile, String status) {
        jdbc.sql("update tutor_profiles set status = ? where id = ?").params(status, profile).update();
    }

    private UUID verification(UUID profile, String status, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into tutor_verifications(id, profile_id, scope, status, evidence_key, reason)
                values (?, ?, 'MATH8_ALGEBRA', ?, ?, ?)
                """).params(id, profile, status, SHA, reason).update();
        return id;
    }

    // ---------------------------------------------------------------- tutor_profile_evidence

    @Test
    void Q3_a_draft_version_takes_its_tutors_ready_evidence_and_a_later_version_reuses_it() {
        UUID tutor = tutor();
        UUID first = profile(tutor, 1, "DRAFT");
        UUID evidence = upload(tutor, "READY");

        link(first, evidence);
        moveProfile(first, "PENDING");
        UUID second = profile(tutor, 2, "DRAFT");
        link(second, evidence);

        assertThat(links(first)).isOne();
        assertThat(links(second)).isOne();
        assertThatThrownBy(() -> link(second, evidence)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void evidence_that_is_not_ready_is_refused() {
        UUID tutor = tutor();
        UUID draft = profile(tutor, 1, "DRAFT");
        for (String state : new String[] {"UPLOADING", "QUARANTINED", "FAILED", "DELETED"}) {
            UUID evidence = upload(tutor, state);
            assertThatThrownBy(() -> link(draft, evidence)).as(state)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ready evidence of the profile's own tutor");
        }
        assertThat(links(draft)).isZero();
    }

    @Test
    void INV_06_another_tutors_evidence_is_refused() {
        UUID draft = profile(tutor(), 1, "DRAFT");
        UUID foreign = upload(tutor(), "READY");

        assertThatThrownBy(() -> link(draft, foreign)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(links(draft)).isZero();
    }

    @Test
    void INV_54_a_version_that_left_draft_takes_no_new_evidence() {
        UUID tutor = tutor();
        UUID evidence = upload(tutor, "READY");
        int version = 1;
        for (String status : new String[] {"PENDING", "APPROVED", "REJECTED", "RETIRED"}) {
            UUID submitted = profile(tutor, version++, status);
            assertThatThrownBy(() -> link(submitted, evidence)).as(status)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void INV_54_the_evidence_of_a_submitted_version_can_be_neither_removed_nor_swapped() {
        UUID tutor = tutor();
        UUID version = profile(tutor, 1, "DRAFT");
        UUID evidence = upload(tutor, "READY");
        UUID other = upload(tutor, "READY");
        link(version, evidence);
        moveProfile(version, "PENDING");

        assertThatThrownBy(() -> jdbc.sql("delete from tutor_profile_evidence where profile_id = ?")
                        .param(version).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("frozen");
        assertThatThrownBy(() -> jdbc.sql("update tutor_profile_evidence set upload_id = ? where profile_id = ?")
                        .params(other, version).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(links(version)).isOne();
    }

    @Test
    void a_draft_can_still_drop_evidence_before_it_is_submitted() {
        UUID tutor = tutor();
        UUID draft = profile(tutor, 1, "DRAFT");
        link(draft, upload(tutor, "READY"));

        int deleted = jdbc.sql("delete from tutor_profile_evidence where profile_id = ?").param(draft).update();

        assertThat(deleted).isOne();
    }

    @Test
    void a_link_needs_an_existing_version_and_upload() {
        UUID tutor = tutor();
        UUID draft = profile(tutor, 1, "DRAFT");
        UUID evidence = upload(tutor, "READY");

        assertThatThrownBy(() -> link(UUID.randomUUID(), evidence)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> link(draft, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- guard_verification (OP-24)

    @Test
    void OP_24_a_verification_of_a_retired_version_never_changes() {
        UUID version = profile(tutor(), 1, "PENDING");
        UUID needsInfo = verification(version, "PENDING", "Bo sung bang cap");
        moveProfile(version, "RETIRED");

        for (String change : new String[] {
            "status = 'REJECTED'", "reason = 'Ly do khac'", "reason = null", "reviewed_at = now()",
            "assigned_to = (select id from accounts limit 1)", "revision = revision + 1",
        }) {
            assertThatThrownBy(() -> jdbc.sql("update tutor_verifications set " + change + " where id = ?")
                            .param(needsInfo).update())
                    .as(change)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("retired");
        }
        assertThat(jdbc.sql("select concat_ws('|', status, reason) from tutor_verifications where id = ?")
                .param(needsInfo).query(String.class).single()).isEqualTo("PENDING|Bo sung bang cap");
    }

    @Test
    void OP_24_no_verification_is_opened_on_a_retired_version() {
        UUID retired = profile(tutor(), 1, "RETIRED");

        assertThatThrownBy(() -> verification(retired, "PENDING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_verification_of_a_version_still_pending_can_still_be_changed() {
        UUID version = profile(tutor(), 1, "PENDING");
        UUID waiting = verification(version, "PENDING", null);

        int updated = jdbc.sql("update tutor_verifications set reason = 'Bo sung bang cap', revision = 2 where id = ?")
                .param(waiting).update();

        assertThat(updated).isOne();
    }

    // ---------------------------------------------------------------- upgrade with data

    @Test
    void a_database_with_profiles_and_verifications_upgrades_from_v10() throws SQLException {
        String database = "tb_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(PostgresTestSupport.POSTGRES.getJdbcUrl(),
                PostgresTestSupport.POSTGRES.getUsername(), PostgresTestSupport.POSTGRES.getPassword())) {
            admin.createStatement().execute("create database " + database + " owner tutorbridge_migrator");
        }
        String url = PostgresTestSupport.POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        try (Connection admin = DriverManager.getConnection(url,
                PostgresTestSupport.POSTGRES.getUsername(), PostgresTestSupport.POSTGRES.getPassword())) {
            admin.createStatement().execute("create extension if not exists vector");
        }

        migrate(url, "10");
        UUID tutor = UUID.randomUUID();
        UUID retired = UUID.randomUUID();
        UUID verification = UUID.randomUUID();
        try (Connection migrator = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)) {
            var statement = migrator.createStatement();
            statement.execute("set search_path = tutorbridge, public");
            statement.execute("insert into accounts(id, role, status) values ('" + tutor + "', 'TUTOR', 'ACTIVE')");
            statement.execute("""
                    insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60)
                    values ('%s', '%s', 1, 'RETIRED', '%s', 12, 180000)
                    """.formatted(retired, tutor, "Mo ta cach day ".repeat(5)));
            statement.execute("""
                    insert into tutor_verifications(id, profile_id, scope, status, evidence_key, reason)
                    values ('%s', '%s', 'MATH8_ALGEBRA', 'PENDING', '%s', 'Bo sung')
                    """.formatted(verification, retired, SHA));
        }
        migrate(url, "latest");

        try (Connection migrator = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)) {
            var statement = migrator.createStatement();
            statement.execute("set search_path = tutorbridge, public");
            var kept = statement.executeQuery(
                    "select status, reason from tutor_verifications where id = '" + verification + "'");
            assertThat(kept.next()).isTrue();
            assertThat(kept.getString(1)).isEqualTo("PENDING");
            assertThat(kept.getString(2)).isEqualTo("Bo sung");
            assertThatThrownBy(() -> statement.execute(
                    "update tutor_verifications set status = 'REJECTED' where id = '" + verification + "'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("retired");
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
