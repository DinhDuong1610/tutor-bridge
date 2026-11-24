package com.tutorbridge.platform.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.Cursor;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * listOwnNotifications and markNotificationRead (UC15, TX30, TB-21). Recipient only: any other caller
 * learns nothing about a notification, not even that it exists (INV-06, AC-UC15-03).
 */
@Service
public class NotificationService {

    /** The {@code Notification} schema of the contract. */
    public record Notification(UUID id, String eventType, String targetType, UUID targetId, String targetVersion,
            Instant createdAt, boolean read) {}

    static final String MARK_READ = "MARK_NOTIFICATION_READ";

    private static final String SELECT = """
            select id, template_code, target_type, target_id, target_version, created_at, read_at is not null as read
            from notifications
            """;

    private final JdbcClient jdbc;
    private final CommandReceipts receipts;
    private final TransactionOperations transactions;
    private final Clock clock;

    NotificationService(JdbcClient jdbc, CommandReceipts receipts, TransactionOperations transactions, Clock clock) {
        this.jdbc = jdbc;
        this.receipts = receipts;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** The caller's own notifications, newest first by the keyset (created_at, id), through V19. */
    public Page<Notification> list(Actor actor, String cursor, Integer limit) {
        int size = Page.limit(limit);
        Cursor after = Cursor.decode(cursor);
        String position = after == null ? "" : "and (created_at, id) < (:afterAt, :afterId)";
        var query = jdbc.sql(SELECT + """
                where recipient_id = :recipient %s
                order by created_at desc, id desc
                limit :limit
                """.formatted(position)).param("recipient", actor.accountId()).param("limit", size + 1);
        if (after != null) {
            query = query.param("afterAt", at(after.at())).param("afterId", after.id());
        }
        return Page.of(query.query(NotificationService::row).list(), size, n -> new Cursor(n.createdAt(), n.id()));
    }

    /**
     * Marks the caller's own notification read, once: reading it again keeps the first time. A replay with
     * the same key answers the notification as it is, which only ever stays read.
     */
    public Notification markRead(Actor actor, UUID notificationId, IdempotencyKey key) {
        return transactions.execute(status -> {
            Receipt receipt = receipts.begin(actor.accountId(), MARK_READ, key.value(),
                    Map.of("notificationId", notificationId.toString()));
            Notification own = find(actor, notificationId).orElseThrow(NotificationService::notFound);
            if (receipt.replay()) {
                // INV-22: the recipient is checked again above.
                return own;
            }
            jdbc.sql("update notifications set read_at = ? where id = ? and recipient_id = ? and read_at is null")
                    .params(at(clock.instant()), notificationId, actor.accountId()).update();
            receipts.succeed(receipt.id(), "NOTIFICATION", notificationId);
            return find(actor, notificationId).orElseThrow();
        });
    }

    private Optional<Notification> find(Actor actor, UUID notificationId) {
        return jdbc.sql(SELECT + " where id = ? and recipient_id = ?").params(notificationId, actor.accountId())
                .query(NotificationService::row).optional();
    }

    private static Notification row(ResultSet rs, int n) throws SQLException {
        return new Notification(rs.getObject("id", UUID.class), rs.getString("template_code"),
                rs.getString("target_type"), rs.getObject("target_id", UUID.class), rs.getString("target_version"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getBoolean("read"));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Không tìm thấy thông báo.");
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
