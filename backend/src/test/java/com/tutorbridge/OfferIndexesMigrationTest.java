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
 * V18 (TB-18, Đính 25/11/2026 on #18 Q7): listOffers reads a tutor's offers and the offers of a parent's
 * needs through an index, and the expiry job finds the overdue PENDING offers without a scan. The database
 * is filled at V17 and then moved to the latest version, so V18 is also checked on a table with rows.
 */
class OfferIndexesMigrationTest extends IntegrationTest {

    private static final int TUTORS = 2000;
    private static final int OFFERS_PER_TUTOR = 5;

    private static String url;
    private static UUID tutor;
    private static UUID need;
    private static UUID student;

    @BeforeAll
    static void a_database_with_offers_moved_from_v17_to_the_latest_version() throws SQLException {
        String database = "tb_offers_" + UUID.randomUUID().toString().replace("-", "");
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

        migrate("17");
        try (Connection admin = DriverManager.getConnection(url,
                PostgresTestSupport.POSTGRES.getUsername(), PostgresTestSupport.POSTGRES.getPassword())) {
            var statement = admin.createStatement();
            // Only the shape of the rows matters to the planner. Replica mode skips the foreign keys and the
            // schedule triggers, which would need a whole need, run and calendar for each of the 10 000 rows;
            // the CHECK constraints still hold. Each tutor has 5 offers, each on another need, and only one in
            // twenty is still PENDING, as most offers end.
            statement.execute("set session_replication_role = replica");
            statement.execute("""
                    insert into tutorbridge.class_offers(id, need_id, need_version_id, tutor_id, profile_id,
                        tutor_calendar_id, student_calendar_id, run_id, state, goal_snapshot, duration_minutes,
                        frequency, cycle_start_date, rate_vnd_60, price_vnd_session, sent_at, expires_at, concluded_at,
                        reason_code)
                    select gen_random_uuid(), md5('need' || (n / %d))::uuid, gen_random_uuid(),
                           md5('tutor' || (n %% %d))::uuid, gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                           gen_random_uuid(), case when n %% 20 = 0 then 'PENDING' else 'EXPIRED' end,
                           repeat('Muc tieu ', 5), 90, 1, current_date + 14, 100000, 150000,
                           now() - make_interval(hours => n %% 500), now() - make_interval(hours => n %% 500) + interval '48 hours',
                           case when n %% 20 = 0 then null else now() end,
                           case when n %% 20 = 0 then null else 'OFFER_EXPIRED' end
                    from generate_series(1, %d) n
                    """.formatted(OFFERS_PER_TUTOR, TUTORS, TUTORS * OFFERS_PER_TUTOR));
            // The needs of those offers, 5 per child, as listOffers of a parent reads them through V13.
            statement.execute("""
                    insert into tutorbridge.learning_needs(id, student_id, status)
                    select md5('need' || k)::uuid, md5('student' || (k / 5))::uuid, 'CLOSED'
                    from generate_series(0, %d) k
                    """.formatted(TUTORS));
            ResultSet any = statement.executeQuery("select tutor_id, need_id from tutorbridge.class_offers limit 1");
            any.next();
            tutor = any.getObject(1, UUID.class);
            need = any.getObject(2, UUID.class);
            ResultSet owner = statement.executeQuery(
                    "select student_id from tutorbridge.learning_needs where id = '" + need + "'");
            owner.next();
            student = owner.getObject(1, UUID.class);
        }
        migrate("latest");
        try (Connection migrator = migrator()) {
            migrator.createStatement().execute("analyze class_offers");
            migrator.createStatement().execute("analyze learning_needs");
        }
    }

    @Test
    void v18_keeps_the_offers_that_were_there_before_it() throws SQLException {
        try (Connection migrator = migrator()) {
            ResultSet count = migrator.createStatement().executeQuery("select count(*) from class_offers");
            count.next();
            assertThat(count.getLong(1)).isEqualTo((long) TUTORS * OFFERS_PER_TUTOR);
        }
    }

    @Test
    void the_indexes_order_offers_newest_first_and_keep_only_pending_ones_for_expiry() {
        assertThat(definition("class_offers_tutor_order")).contains("(tutor_id, sent_at DESC, id DESC)");
        assertThat(definition("class_offers_need_order")).contains("(need_id, sent_at DESC, id DESC)");
        assertThat(definition("class_offers_pending_expiry")).contains("(expires_at)")
                .contains("WHERE (state = 'PENDING'::text)");
    }

    @Test
    void listOffers_reads_the_pages_of_a_tutor_through_the_index() throws SQLException {
        for (String position : List.of("", "and (o.sent_at, o.id) < (now(), '00000000-0000-0000-0000-000000000000'::uuid)")) {
            List<String> plan = explain("""
                    select o.id from class_offers o
                    where o.tutor_id = ? %s
                    order by o.sent_at desc, o.id desc
                    limit 21""".formatted(position), tutor);
            assertThat(plan).as(position).anyMatch(line -> line.contains("class_offers_tutor_order"));
            assertThat(plan).as(position).noneMatch(line -> line.contains("Seq Scan"));
        }
    }

    @Test
    void listOffers_reads_the_offers_of_a_parents_needs_through_the_index() throws SQLException {
        // A parent's needs are a short list of ids; each is read through the index, then the few rows sorted.
        List<String> plan = explain("""
                select o.id from class_offers o
                where o.need_id = any(array[?, '00000000-0000-0000-0000-000000000000'::uuid])
                order by o.sent_at desc, o.id desc
                limit 21""", need);
        assertThat(plan).anyMatch(line -> line.contains("class_offers_need_order"));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan"));
    }

    @Test
    void listOffers_reads_a_parents_offers_through_the_needs_of_their_children() throws SQLException {
        // The where of OfferRepository.listByStudents: the children's needs through V13, their offers through V18.
        List<String> plan = explain("""
                select o.id from class_offers o join learning_needs n on n.id = o.need_id
                where n.student_id = any(array[?, '00000000-0000-0000-0000-000000000000'::uuid])
                order by o.sent_at desc, o.id desc
                limit 21""", student);
        assertThat(plan).anyMatch(line -> line.contains("class_offers_need_order"));
        assertThat(plan).anyMatch(line -> line.contains("learning_needs_student_order"));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan"));
    }

    @Test
    void the_expiry_job_finds_overdue_pending_offers_through_the_partial_index() throws SQLException {
        List<String> plan = explain("""
                select o.id from class_offers o
                where o.state = 'PENDING' and o.expires_at <= now()
                order by o.expires_at
                limit 100""", null);
        assertThat(plan).anyMatch(line -> line.contains("class_offers_pending_expiry"));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan"));
    }

    private String definition(String index) {
        return jdbc.sql("select indexdef from pg_indexes where indexname = ?").param(index)
                .query(String.class).single();
    }

    private static List<String> explain(String query, UUID id) throws SQLException {
        try (Connection migrator = migrator();
                PreparedStatement statement = migrator.prepareStatement("explain " + query)) {
            if (id != null) {
                statement.setObject(1, id);
            }
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
