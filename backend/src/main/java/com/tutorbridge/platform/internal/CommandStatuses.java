package com.tutorbridge.platform.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.CommandResults;
import com.tutorbridge.platform.api.CommandReceipts;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * getCommandStatus and findCommandStatus (TX16, FR-37, TB-22): after a lost response the client asks what
 * became of its command instead of guessing (INV-24).
 *
 * <p>Only the receipt's own actor sees it; any other receipt, a missing one and one past its
 * {@code expires_at} all answer the same 404 (INV-06, INV-26; Huy, 24/11/2026). The answer names the result
 * only while the actor may still see it, as the module that owns the result says now (INV-22); otherwise
 * the state and error stay and the result is left out (Huy, 24/11/2026).
 *
 * <p>A receipt commits with its command (INV-21), so another request still running is not visible yet and
 * also answers 404: that never proves the command failed, so the client resends with the same key.
 */
@Service
public class CommandStatuses {

    /** {@code CommandStatus}. */
    public record CommandStatus(UUID operationId, String commandType, CommandReceipts.State state,
            String resultType, UUID resultId, String errorCode, Instant expiresAt) {

        /** A receipt changes once, from PROCESSING to SUCCEEDED or FAILED. */
        public long version() {
            return state == CommandReceipts.State.PROCESSING ? 1 : 2;
        }
    }

    private record Row(UUID id, String commandType, String state, String resultType, UUID resultId,
            String errorCode, Instant expiresAt) {}

    private static final String COLUMNS =
            "select id, command_type, state, result_type, result_id, error_code, expires_at from command_receipts";

    private final JdbcClient jdbc;
    private final Map<String, CommandResults> results;
    private final Clock clock;

    CommandStatuses(JdbcClient jdbc, List<CommandResults> modules, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        Map<String, CommandResults> byType = new HashMap<>();
        for (CommandResults module : modules) {
            for (String type : module.resultTypes()) {
                if (byType.putIfAbsent(type, module) != null) {
                    throw new IllegalStateException("Two modules answer for result type " + type);
                }
            }
        }
        this.results = Map.copyOf(byType);
    }

    /** getCommandStatus. */
    public CommandStatus get(Actor actor, UUID operationId) {
        return status(actor, jdbc.sql(COLUMNS + " where id = ? and actor_id = ? and expires_at > ?")
                .params(operationId, actor.accountId(), at(clock.instant()))
                .query(CommandStatuses::row).optional());
    }

    /** findCommandStatus: the original command type and key of the actor (unique per actor, V1). */
    public CommandStatus find(Actor actor, String commandType, String idempotencyKey) {
        return status(actor, jdbc.sql(COLUMNS
                        + " where actor_id = ? and command_type = ? and idempotency_key = ? and expires_at > ?")
                .params(actor.accountId(), commandType, idempotencyKey, at(clock.instant()))
                .query(CommandStatuses::row).optional());
    }

    private CommandStatus status(Actor actor, Optional<Row> found) {
        Row row = found.orElseThrow(CommandStatuses::notFound);
        boolean visible = row.resultType() != null && row.resultId() != null
                && Optional.ofNullable(results.get(row.resultType()))
                        .map(module -> module.visibleTo(actor, row.resultType(), row.resultId()))
                        .orElse(false);
        return new CommandStatus(row.id(), row.commandType(), CommandReceipts.State.valueOf(row.state()),
                visible ? row.resultType() : null, visible ? row.resultId() : null, row.errorCode(),
                row.expiresAt());
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getString("command_type"), rs.getString("state"),
                rs.getString("result_type"), rs.getObject("result_id", UUID.class), rs.getString("error_code"),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "OPERATION_NOT_FOUND",
                "Chưa có kết quả cho thao tác này. Gửi lại đúng thao tác đó để biết kết quả.");
    }
}
