package com.tutorbridge.platform.api;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency receipts. Every method joins the caller's transaction, so the receipt commits or
 * rolls back with the business write and the outbox event (INV-21).
 *
 * <p>A concurrent duplicate blocks on the unique index until the first transaction ends, then
 * either replays its result or, after a rollback, starts as a new command.
 *
 * <p>On a replay the caller must reauthorize before returning the result (INV-22).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class CommandReceipts {

    public enum State { PROCESSING, SUCCEEDED, FAILED }

    public record Receipt(
            UUID id, State state, String resultType, UUID resultId, String errorCode, boolean replay) {}

    private record StoredReceipt(
            UUID id, String payloadSha256, String state, String resultType, UUID resultId, String errorCode) {}

    private final JdbcClient jdbc;

    public CommandReceipts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Receipt begin(UUID actorId, String commandType, String idempotencyKey, Object payload) {
        // Checked again here so a caller that skips the web layer, such as a job, keeps the rule.
        IdempotencyKey.check(idempotencyKey);
        String payloadSha256 = CanonicalJson.sha256(payload);
        UUID id = UUID.randomUUID();
        int inserted = jdbc.sql("""
                insert into command_receipts
                    (id, actor_id, command_type, idempotency_key, payload_sha256, state, expires_at)
                values (?, ?, ?, ?, ?, 'PROCESSING', now() + interval '7 days')
                on conflict (actor_id, command_type, idempotency_key) do nothing
                """).params(id, actorId, commandType, idempotencyKey, payloadSha256).update();
        if (inserted == 1) {
            return new Receipt(id, State.PROCESSING, null, null, null, false);
        }

        StoredReceipt stored = jdbc.sql("""
                select id, payload_sha256, state, result_type, result_id, error_code
                from command_receipts
                where actor_id = ? and command_type = ? and idempotency_key = ?
                for update
                """).params(actorId, commandType, idempotencyKey).query(StoredReceipt.class).single();
        if (!stored.payloadSha256().equals(payloadSha256)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "Khóa gửi lại này đã dùng cho một nội dung khác.");
        }
        return new Receipt(stored.id(), State.valueOf(stored.state()), stored.resultType(),
                stored.resultId(), stored.errorCode(), true);
    }

    public void succeed(UUID receiptId, String resultType, UUID resultId) {
        finish(jdbc.sql("""
                update command_receipts set state = 'SUCCEEDED', result_type = ?, result_id = ?
                where id = ? and state = 'PROCESSING'
                """).params(resultType, resultId, receiptId).update(), receiptId);
    }

    public void fail(UUID receiptId, String errorCode) {
        finish(jdbc.sql("""
                update command_receipts set state = 'FAILED', error_code = ?
                where id = ? and state = 'PROCESSING'
                """).params(errorCode, receiptId).update(), receiptId);
    }

    private void finish(int updated, UUID receiptId) {
        if (updated != 1) {
            throw new IllegalStateException("Receipt is not processing: " + receiptId);
        }
    }
}
