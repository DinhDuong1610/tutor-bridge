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
 * V20 (#97, Đính 27/11/2026): listClasses reads a tutor's classes and the classes of a parent's children
 * through an index, newest first. The database is filled at V19 and then moved to the latest version, so
 * V20 is also checked on a table with rows.
 */
class ClassListIndexesMigrationTest extends IntegrationTest {

    private static final int TUTORS = 2000;
    private static final int CLASSES_PER_TUTOR = 5;

    private static String url;
    private static UUID tutor;
    private static UUID student;

    @BeforeAll
    static void a_database_with_classes_moved_from_v19_to_the_latest_version() throws SQLException {
        String database = "tb_classes_" + UUID.randomUUID().toString().replace("-", "");
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

        migrate("19");
        try (Connection admin = DriverManager.getConnection(url,
                PostgresTestSupport.POSTGRES.getUsername(), PostgresTestSupport.POSTGRES.getPassword())) {
            var statement = admin.createStatement();
            // Only the shape of the rows matters to the planner. Replica mode skips the foreign keys, which would
            // need a whole need, offer, student and tutor for each of the 10 000 rows; the CHECK constraints
            // still hold. Each tutor has 5 classes, each child one.
            statement.execute("set session_replication_role = replica");
            statement.execute("""
                    insert into tutorbridge.classes(id, originating_need_id, accepted_offer_id, student_id, tutor_id,
                        status, duration_minutes, price_vnd_session, created_at)
                    select gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), md5('student' || n)::uuid,
                           md5('tutor' || (n %% %d))::uuid, 'ACTIVE', 90, 150000,
                           now() - make_interval(hours => n %% 500)
                    from generate_series(1, %d) n
                    """.formatted(TUTORS, TUTORS * CLASSES_PER_TUTOR));
            ResultSet any = statement.executeQuery("select tutor_id, student_id from tutorbridge.classes limit 1");
            any.next();
            tutor = any.getObject(1, UUID.class);
            student = any.getObject(2, UUID.class);
        }
        migrate("latest");
        try (Connection migrator = migrator()) {
            migrator.createStatement().execute("analyze classes");
        }
    }

    @Test
    void v20_keeps_the_classes_that_were_there_before_it() throws SQLException {
        try (Connection migrator = migrator()) {
            ResultSet count = migrator.createStatement().executeQuery("select count(*) from classes");
            count.next();
            assertThat(count.getLong(1)).isEqualTo((long) TUTORS * CLASSES_PER_TUTOR);
        }
    }

    @Test
    void the_indexes_order_classes_newest_first() {
        assertThat(definition("classes_tutor_order")).contains("(tutor_id, created_at DESC, id DESC)");
        assertThat(definition("classes_student_order")).contains("(student_id, created_at DESC, id DESC)");
    }

    @Test
    void listClasses_reads_the_pages_of_a_tutor_through_the_index() throws SQLException {
        for (String position : List.of("",
                "and (c.created_at, c.id) < (now(), '00000000-0000-0000-0000-000000000000'::uuid)")) {
            List<String> plan = explain("""
                    select c.id from classes c
                    where c.tutor_id = ? %s
                    order by c.created_at desc, c.id desc
                    limit 21""".formatted(position), tutor);
            assertThat(plan).as(position).anyMatch(line -> line.contains("classes_tutor_order"));
            assertThat(plan).as(position).noneMatch(line -> line.contains("Seq Scan"));
        }
    }

    @Test
    void listClasses_reads_the_classes_of_a_parents_children_through_the_index() throws SQLException {
        // A parent's children are a short list of ids; each is read through the index, then the few rows sorted.
        List<String> plan = explain("""
                select c.id from classes c
                where c.student_id = any(array[?, '00000000-0000-0000-0000-000000000000'::uuid])
                order by c.created_at desc, c.id desc
                limit 21""", student);
        assertThat(plan).anyMatch(line -> line.contains("classes_student_order"));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan"));
    }

    private String definition(String index) {
        return jdbc.sql("select indexdef from pg_indexes where indexname = ?").param(index)
                .query(String.class).single();
    }

    private static List<String> explain(String query, UUID id) throws SQLException {
        try (Connection migrator = migrator();
                PreparedStatement statement = migrator.prepareStatement("explain " + query)) {
            statement.setObject(1, id);
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
