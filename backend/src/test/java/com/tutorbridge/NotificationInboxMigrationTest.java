package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * V19 (TB-21, Đính 25/11/2026 on #21 Q1): notifications keep their own time and target version. They
 * outlive the outbox event they came from (no foreign key since V6) and stay one per event and recipient.
 */
class NotificationInboxMigrationTest extends IntegrationTest {

    private UUID event() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into outbox_events(id, aggregate_type, aggregate_id, event_type, payload, dedupe_key, delivered_at)
                values (?, 'OFFER', ?, 'OFFER_SENT', '{}'::jsonb, ?, now() - interval '8 days')
                """).params(id, UUID.randomUUID(), "test:" + id).update();
        return id;
    }

    private UUID notification(UUID recipient, UUID event) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into notifications(id, recipient_id, event_id, template_code, target_type, target_id, target_version)
                values (?, ?, ?, 'OFFER_SENT', 'OFFER', ?, '1')
                """).params(id, recipient, event, UUID.randomUUID()).update();
        return id;
    }

    @Test
    void a_notification_gets_its_time_and_keeps_its_target_version() {
        UUID id = notification(newAccount(), event());

        assertThat(jdbc.sql("select created_at is not null and target_version = '1' from notifications where id = ?")
                .param(id).query(Boolean.class).single()).isTrue();
    }

    @Test
    void TB_24_the_delivered_event_can_be_deleted_and_the_notification_stays() {
        UUID event = event();
        UUID id = notification(newAccount(), event);

        jdbc.sql("delete from outbox_events where id = ?").param(event).update();

        assertThat(jdbc.sql("select count(*) from notifications where id = ?").param(id).query(Integer.class).single())
                .isOne();
    }

    @Test
    void TX30_one_notification_per_event_and_recipient() {
        UUID recipient = newAccount();
        UUID event = event();
        notification(recipient, event);
        notification(newAccount(), event);

        assertThatThrownBy(() -> notification(recipient, event)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void the_inbox_index_orders_a_recipients_notifications_newest_first() {
        assertThat(jdbc.sql("select indexdef from pg_indexes where indexname = 'notifications_recipient_order'")
                .query(String.class).single()).contains("(recipient_id, created_at DESC, id DESC)");
    }

    /**
     * listOwnNotifications reads one recipient's pages through the V19 index on a filled table: 2 000
     * recipients with 10 notifications each, written at V18 and then moved to the latest version.
     */
    @Test
    void listOwnNotifications_reads_the_pages_of_a_recipient_through_the_index() throws SQLException {
        String database = "tb_inbox_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(PostgresTestSupport.POSTGRES.getJdbcUrl(),
                PostgresTestSupport.POSTGRES.getUsername(), PostgresTestSupport.POSTGRES.getPassword())) {
            admin.createStatement().execute("create database " + database + " owner tutorbridge_migrator");
        }
        String url = PostgresTestSupport.POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        try (Connection admin = DriverManager.getConnection(url,
                PostgresTestSupport.POSTGRES.getUsername(), PostgresTestSupport.POSTGRES.getPassword())) {
            admin.createStatement().execute("create extension if not exists vector");
        }
        migrate(url, "18");
        UUID recipient;
        try (Connection admin = DriverManager.getConnection(url,
                PostgresTestSupport.POSTGRES.getUsername(), PostgresTestSupport.POSTGRES.getPassword())) {
            var statement = admin.createStatement();
            statement.execute("""
                    insert into tutorbridge.accounts(id, role, status)
                    select md5('recipient' || k)::uuid, 'TUTOR', 'ACTIVE' from generate_series(0, 1999) k
                    """);
            statement.execute("""
                    insert into tutorbridge.notifications(id, recipient_id, event_id, template_code, target_type, target_id)
                    select gen_random_uuid(), md5('recipient' || (n % 2000))::uuid, gen_random_uuid(), 'OFFER_SENT',
                           'OFFER', gen_random_uuid()
                    from generate_series(1, 20000) n
                    """);
            ResultSet any = statement.executeQuery("select md5('recipient0')::uuid");
            any.next();
            recipient = any.getObject(1, UUID.class);
        }
        migrate(url, "latest");
        try (Connection migrator = migrator(url)) {
            // Rows written before V19 all take the time of the migration; real ones are spread over time.
            migrator.createStatement().execute("""
                    update notifications set created_at = now() - make_interval(mins => (random() * 100000)::int)""");
            migrator.createStatement().execute("analyze notifications");
            for (String position : List.of("",
                    "and (created_at, id) < (now(), '00000000-0000-0000-0000-000000000000'::uuid)")) {
                // The where and order by of NotificationService.list.
                List<String> plan = explain(migrator, """
                        select id from notifications
                        where recipient_id = ? %s
                        order by created_at desc, id desc
                        limit 21""".formatted(position), recipient);
                // Only this recipient's rows, through an index on recipient_id. With ten rows per recipient the
                // planner may take the UNIQUE(recipient_id, event_id) index and sort those ten for a later page,
                // which is cheaper and still reads no one else's rows (as NeedsByStudentMigrationTest notes).
                assertThat(plan).as(position).anyMatch(line -> line.contains("Index Cond: (recipient_id = "));
                assertThat(plan).as(position).noneMatch(line -> line.contains("Seq Scan"));
                if (position.isEmpty()) {
                    assertThat(plan).anyMatch(line -> line.contains("notifications_recipient_order"));
                }
            }
        }
    }

    private static List<String> explain(Connection connection, String query, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("explain " + query)) {
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

    private static Connection migrator(String url) throws SQLException {
        Connection connection = DriverManager.getConnection(url, "tutorbridge_migrator",
                PostgresTestSupport.MIGRATOR_PASSWORD);
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
