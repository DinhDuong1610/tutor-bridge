package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
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
}
