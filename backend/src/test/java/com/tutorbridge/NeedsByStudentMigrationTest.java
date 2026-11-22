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
 * V13 (#187): the needs of one child are read through an index, for listNeeds and for the "already has an
 * open need" check of createNeedDraft, which runs under the child's resource lock (INV-16). The database
 * is filled at V12 and then moved to the latest version, so V13 is also checked on a table with rows.
 */
class NeedsByStudentMigrationTest extends IntegrationTest {

    private static final String INDEX = "learning_needs_student_order";
    private static final int STUDENTS = 2000;
    private static final int NEEDS_PER_STUDENT = 5;

    private static String url;
    private static UUID student;

    @BeforeAll
    static void a_database_with_needs_moved_from_v12_to_the_latest_version() throws SQLException {
        String database = "tb_needs_" + UUID.randomUUID().toString().replace("-", "");
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
            UUID parent = UUID.randomUUID();
            statement.execute("insert into accounts(id, role, status) values ('" + parent + "', 'PARENT', 'ACTIVE')");
            statement.execute("""
                    insert into students(id, parent_id, display_name)
                    select gen_random_uuid(), '%s', 'Hoc sinh ' || n from generate_series(1, %d) n
                    """.formatted(parent, STUDENTS));
            // Every child but the newest need is CLOSED, as Q4 of #16 allows one open need per child.
            statement.execute("""
                    insert into learning_needs(id, student_id, status, created_at)
                    select gen_random_uuid(), s.id, case when k = %d then 'DRAFT' else 'CLOSED' end,
                           now() - make_interval(days => %d - k)
                    from students s cross join generate_series(1, %d) k
                    """.formatted(NEEDS_PER_STUDENT, NEEDS_PER_STUDENT, NEEDS_PER_STUDENT));
            ResultSet any = statement.executeQuery("select id from students limit 1");
            any.next();
            student = any.getObject(1, UUID.class);
        }
        migrate("latest");
        try (Connection migrator = migrator()) {
            migrator.createStatement().execute("analyze learning_needs");
        }
    }

    @Test
    void v13_keeps_the_needs_that_were_there_before_it() throws SQLException {
        try (Connection migrator = migrator()) {
            ResultSet count = migrator.createStatement().executeQuery("select count(*) from learning_needs");
            count.next();
            assertThat(count.getLong(1)).isEqualTo((long) STUDENTS * NEEDS_PER_STUDENT);
        }
    }

    @Test
    void the_index_orders_the_needs_of_a_child_newest_first() {
        String definition = jdbc.sql("select indexdef from pg_indexes where indexname = ?").param(INDEX)
                .query(String.class).single();
        assertThat(definition).contains("(student_id, created_at DESC, id DESC)");
    }

    @Test
    void listNeeds_reads_the_first_page_of_a_child_through_the_index() throws SQLException {
        // The where and order by of NeedRepository.listByStudent without a cursor.
        List<String> plan = explain("""
                select n.id from learning_needs n
                where n.student_id = ?
                order by n.created_at desc, n.id desc
                limit 21""", student);
        assertThat(plan).anyMatch(line -> line.contains(INDEX));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan"));
        // No check on Sort: with five rows per child the planner may read them through the index and sort
        // those five, which is cheaper than an ordered index scan and still reads only this child's rows.
    }

    @Test
    void listNeeds_reads_a_later_page_of_a_child_through_the_index() throws SQLException {
        // The same with the keyset cursor of NeedRepository.listByStudent.
        List<String> plan = explain("""
                select n.id from learning_needs n
                where n.student_id = ? and (n.created_at, n.id) < (now(), '00000000-0000-0000-0000-000000000000'::uuid)
                order by n.created_at desc, n.id desc
                limit 21""", student);
        assertThat(plan).anyMatch(line -> line.contains(INDEX));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan"));
    }

    @Test
    void the_open_need_check_reads_one_child_through_the_index() throws SQLException {
        // NeedRepository.hasOpenNeed, which runs while createNeedDraft holds the child's lock.
        List<String> plan = explain(
                "select exists(select 1 from learning_needs where student_id = ? and status <> 'CLOSED')", student);
        assertThat(plan).anyMatch(line -> line.contains(INDEX));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan"));
    }

    private static List<String> explain(String query, UUID studentId) throws SQLException {
        try (Connection migrator = migrator();
                PreparedStatement statement = migrator.prepareStatement("explain " + query)) {
            statement.setObject(1, studentId);
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
