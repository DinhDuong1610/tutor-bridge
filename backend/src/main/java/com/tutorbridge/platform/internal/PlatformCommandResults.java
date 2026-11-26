package com.tutorbridge.platform.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.CommandResults;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The results of platform's own commands, for the command status lookup (TX16, TB-22): {@code NOTIFICATION}
 * (markNotificationRead, TB-21), shown as listOwnNotifications reads it, to its recipient only, now (INV-22).
 * A notification gone since (INV-26) is not shown.
 */
@Component
class PlatformCommandResults implements CommandResults {

    private final JdbcClient jdbc;

    PlatformCommandResults(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> resultTypes() {
        return Set.of(NotificationService.RESULT_TYPE);
    }

    @Override
    public boolean visibleTo(Actor actor, String resultType, UUID resultId) {
        return NotificationService.RESULT_TYPE.equals(resultType) && jdbc.sql("""
                select exists(select 1 from notifications where id = ? and recipient_id = ?)""")
                .params(resultId, actor.accountId()).query(Boolean.class).single();
    }
}
