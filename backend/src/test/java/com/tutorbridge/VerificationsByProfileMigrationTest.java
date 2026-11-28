package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * V14 (#189): the verifications of one profile version are read through an index, by status. The
 * database is filled at V12 and then moved to the latest version, so V14 is also checked on a table with
 * rows, and every query that filters this table by version is explained as the repository writes it.
 */
class VerificationsByProfileMigrationTest extends IntegrationTest {

    private static final String INDEX = "tutor_verifications_profile_status";
    private static final int TUTORS = 2000;
    /** Per tutor: version 1 approved with two VERIFIED and one REJECTED, version 2 waiting with one PENDING. */
    private static final int VERIFICATIONS_PER_TUTOR = 4;

    private static String url;
    private static UUID pendingVersion;

    @BeforeAll
    static void a_database_with_verifications_moved_from_v12_to_the_latest_version() throws SQLException {
        String database = "tb_verifications_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(
                PostgresTestSupport.POSTGRES.getJdbcUrl(),
                PostgresTestSupport.POSTGRES.getUsername(),
                PostgresTestSupport.POSTGRES.getPassword())) {
            admin.createStatement().execute("create database " + database + " owner tutorbridge_migrator");
        }
        url = PostgresTestSupport.POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        try (Connection admin = DriverManager.getConnection(url,
                PostgresTestSupport.POSTGRES.getUsername(), PostgresTestSupport.POSTGRES.getPassword())) {
            admin.createStatement().execute("create extension if not exists vector");
        }

        migrate("12");
        try (Connection migrator = migrator()) {
            var statement = migrator.createStatement();
            UUID verifier = UUID.randomUUID();
            statement.execute("insert into accounts(id, role, status) values ('" + verifier + "', 'ADMIN', 'ACTIVE')");
            statement.execute("insert into admin_assignments(account_id, capability, granted_by) values ('%s', 'VERIFY', '%s')"
                    .formatted(verifier, verifier));
            statement.execute("""
                    insert into accounts(id, role, status, display_name)
                    select gen_random_uuid(), 'TUTOR', 'ACTIVE', 'Gia su ' || n from generate_series(1, %d) n
                    """.formatted(TUTORS));
            statement.execute("""
                    insert into tutor_profiles(id, tutor_id, version, status, description, experience_months, rate_vnd_60,
                                               current)
                    select gen_random_uuid(), a.id, v, case v when 1 then 'APPROVED' else 'PENDING' end, repeat('x', 50),
                           24, 100000, v = 1
                    from accounts a cross join generate_series(1, 2) v where a.role = 'TUTOR'
                    """);
            statement.execute("""
                    insert into tutor_verifications(id, profile_id, scope, status, evidence_key, assigned_to, reviewed_by,
                                                    reviewed_at)
                    select gen_random_uuid(), p.id, s.scope, s.status, 'k', '%1$s', '%1$s', now()
                    from tutor_profiles p
                    cross join (values ('MATH8_ALGEBRA', 'VERIFIED'), ('MATH8_GEOMETRY', 'VERIFIED'),
                                       ('MATH8_ALGEBRA', 'REJECTED')) s(scope, status)
                    where p.version = 1
                    """.formatted(verifier));
            statement.execute("""
                    insert into tutor_verifications(id, profile_id, scope, status, evidence_key)
                    select gen_random_uuid(), p.id, 'MATH8_ALGEBRA', 'PENDING', 'k' from tutor_profiles p where p.version = 2
                    """);
            ResultSet any = statement.executeQuery("select id from tutor_profiles where version = 2 limit 1");
            any.next();
            pendingVersion = any.getObject(1, UUID.class);
        }
        migrate("latest");
        try (Connection migrator = migrator()) {
            migrator.createStatement().execute("analyze tutor_verifications");
            migrator.createStatement().execute("analyze tutor_profiles");
            migrator.createStatement().execute("analyze accounts");
        }
    }

    @Test
    void v14_keeps_the_verifications_that_were_there_before_it() throws SQLException {
        try (Connection migrator = migrator()) {
            ResultSet count = migrator.createStatement().executeQuery("select count(*) from tutor_verifications");
            count.next();
            assertThat(count.getLong(1)).isEqualTo((long) TUTORS * VERIFICATIONS_PER_TUTOR);
        }
    }

    @Test
    void the_index_is_on_the_version_then_the_status() {
        String definition = jdbc.sql("select indexdef from pg_indexes where indexname = ?").param(INDEX)
                .query(String.class).single();
        assertThat(definition).contains("(profile_id, status)");
    }

    @Test
    void needsInformation_reads_one_version_through_the_index() throws SQLException {
        // TutorProfileRepository.needsInformation.
        List<String> plan = explain("""
                select coalesce(bool_and(reason is not null), false) from tutor_verifications
                where profile_id = ? and status = 'PENDING'""", pendingVersion);
        assertThat(plan).anyMatch(line -> line.contains(INDEX));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan"));
    }

    @Test
    void lockPendingVerifications_reads_one_version_through_the_index_under_the_locks() throws SQLException {
        // TutorVerificationRepository.lockPendingVerifications, run while submitTutorProfile holds the
        // account and profile locks (INV-16).
        List<String> plan = explain("""
                select id, reason from tutor_verifications where profile_id = ? and status = 'PENDING'
                order by id for update""", pendingVersion);
        assertThat(plan).anyMatch(line -> line.contains(INDEX));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan"));
    }

    @Test
    void the_scopes_of_a_page_of_public_tutors_never_scan_the_verifications() throws SQLException {
        // TutorPublicRepository.SELECT with the paging of Parties.matchableTutors (#207): one scope subquery
        // per tutor of the page. Before V14 each one scanned the whole table (Codex, #207 review).
        List<String> plan = explain("""
                select a.id as tutor_id, p.id as profile_id, a.display_name, p.description, p.rate_vnd_60,
                       p.experience_months, p.revision,
                       array(select distinct v.scope from tutor_verifications v
                             where v.profile_id = p.id and v.status = 'VERIFIED' order by 1 limit 20) as scopes
                from accounts a
                join tutor_profiles p on p.tutor_id = a.id and p.current and p.status = 'APPROVED'
                where a.role = 'TUTOR' and a.status = 'ACTIVE' and a.display_name is not null and p.subject = ?
                  and p.rate_vnd_60 <= 200000 and a.id > '00000000-0000-0000-0000-000000000000'::uuid
                order by a.id limit 50""", "MATH8_ALGEBRA");
        assertThat(plan).anyMatch(line -> line.contains(INDEX));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan on tutor_verifications"));
    }

    private static List<String> explain(String query, Object parameter) throws SQLException {
        try (Connection migrator = migrator();
                PreparedStatement statement = migrator.prepareStatement("explain " + query)) {
            statement.setObject(1, parameter);
            List<String> lines = new ArrayList<>();
            try (ResultSet plan = statement.executeQuery()) {
                while (plan.next()) {
                    lines.add(plan.getString(1));
                }
            }
            return lines;
        }
    }

    private static Connection migrator() throws SQLException {
        Connection connection = DriverManager.getConnection(
                url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD);
        connection.createStatement().execute("set search_path = tutorbridge, public");
        return connection;
    }

    private static void migrate(String target) {
        Flyway.configure()
                .dataSource(url, "tutorbridge_migrator", PostgresTestSupport.MIGRATOR_PASSWORD)
                .defaultSchema("public")
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }
}
