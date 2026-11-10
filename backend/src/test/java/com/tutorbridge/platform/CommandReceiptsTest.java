package com.tutorbridge.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorbridge.Concurrently;
import com.tutorbridge.IntegrationTest;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.CommandReceipts.State;
import com.tutorbridge.platform.api.Outbox;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class CommandReceiptsTest extends IntegrationTest {

    private static final String COMMAND = "TEST_COMMAND";
    private static final Map<String, Object> PAYLOAD = Map.of("offerId", "o-1", "revision", 3);

    @Autowired
    CommandReceipts receipts;

    @Autowired
    Outbox outbox;

    @Autowired
    TransactionTemplate tx;

    private static String key() {
        return UUID.randomUUID().toString();
    }

    @Test
    void INV_20_same_key_and_payload_replays_the_stored_result() {
        UUID actor = newAccount();
        String key = key();
        UUID result = UUID.randomUUID();

        Receipt first = tx.execute(s -> {
            Receipt receipt = receipts.begin(actor, COMMAND, key, PAYLOAD);
            receipts.succeed(receipt.id(), "CLASS", result);
            return receipt;
        });
        Receipt replay = tx.execute(s -> receipts.begin(actor, COMMAND, key, Map.of("revision", 3, "offerId", "o-1")));

        assertThat(first.replay()).isFalse();
        assertThat(replay.replay()).isTrue();
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.state()).isEqualTo(State.SUCCEEDED);
        assertThat(replay.resultType()).isEqualTo("CLASS");
        assertThat(replay.resultId()).isEqualTo(result);
    }

    @Test
    void INV_20_same_key_with_other_payload_is_a_409() {
        UUID actor = newAccount();
        String key = key();
        tx.executeWithoutResult(s -> receipts.begin(actor, COMMAND, key, PAYLOAD));

        assertThatThrownBy(() -> tx.executeWithoutResult(
                        s -> receipts.begin(actor, COMMAND, key, Map.of("offerId", "o-2", "revision", 3))))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
    }

    @Test
    void key_is_scoped_to_actor_and_command() {
        UUID actor = newAccount();
        UUID other = newAccount();
        String key = key();

        Receipt first = tx.execute(s -> receipts.begin(actor, COMMAND, key, PAYLOAD));
        Receipt otherActor = tx.execute(s -> receipts.begin(other, COMMAND, key, PAYLOAD));
        Receipt otherCommand = tx.execute(s -> receipts.begin(actor, "OTHER_COMMAND", key, PAYLOAD));

        assertThat(otherActor.replay()).isFalse();
        assertThat(otherCommand.replay()).isFalse();
        assertThat(otherActor.id()).isNotEqualTo(first.id());
        assertThat(otherCommand.id()).isNotEqualTo(first.id());
    }

    @Test
    void key_outside_16_to_128_characters_is_rejected() {
        UUID actor = newAccount();

        for (String key : new String[] {"", "a".repeat(15), "a".repeat(129)}) {
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> receipts.begin(actor, COMMAND, key, PAYLOAD)))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.code()).isEqualTo("IDEMPOTENCY_KEY_INVALID");
                        assertThat(e.status().value()).isEqualTo(422);
                    });
        }
    }

    @Test
    void CR_014_missing_key_is_rejected_as_required() {
        UUID actor = newAccount();

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> receipts.begin(actor, COMMAND, null, PAYLOAD)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
                    assertThat(e.status().value()).isEqualTo(400);
                });
    }

    @Test
    void INV_21_receipt_and_outbox_roll_back_with_the_transaction() {
        UUID actor = newAccount();
        String key = key();
        String dedupeKey = key();

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            receipts.begin(actor, COMMAND, key, PAYLOAD);
            outbox.publish("CLASS", UUID.randomUUID(), "CLASS_CREATED", Map.of(), dedupeKey);
            throw new IllegalStateException("business failure after both writes");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.sql("select count(*) from command_receipts where idempotency_key = ?")
                .param(key).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from outbox_events where dedupe_key = ?")
                .param(dedupeKey).query(Integer.class).single()).isZero();
    }

    @Test
    void INV_21_writes_outside_a_transaction_are_refused() {
        UUID actor = newAccount();

        assertThatThrownBy(() -> receipts.begin(actor, COMMAND, key(), PAYLOAD))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> outbox.publish("CLASS", UUID.randomUUID(), "CLASS_CREATED", Map.of(), key()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void INV_20_concurrent_duplicate_waits_then_replays_the_committed_result() throws Exception {
        UUID actor = newAccount();
        String key = key();
        UUID result = UUID.randomUUID();
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);

        CompletableFuture<Receipt> first = Concurrently.supply(() -> tx.execute(s -> {
            Receipt receipt = receipts.begin(actor, COMMAND, key, PAYLOAD);
            firstInserted.countDown();
            await(finishFirst);
            receipts.succeed(receipt.id(), "CLASS", result);
            return receipt;
        }));
        firstInserted.await(10, TimeUnit.SECONDS);
        CompletableFuture<Receipt> second = Concurrently.supply(
                () -> tx.execute(s -> receipts.begin(actor, COMMAND, key, PAYLOAD)));

        assertThat(second).isNotCompleted();
        Thread.sleep(300);
        assertThat(second).as("must block on the open transaction").isNotCompleted();
        finishFirst.countDown();

        assertThat(second.get(10, TimeUnit.SECONDS).replay()).isTrue();
        assertThat(second.get().resultId()).isEqualTo(result);
        assertThat(second.get().id()).isEqualTo(first.get().id());
    }

    @Test
    void concurrent_duplicate_starts_fresh_when_the_first_transaction_rolls_back() throws Exception {
        UUID actor = newAccount();
        String key = key();
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);

        CompletableFuture<Void> first = Concurrently.run(() -> tx.executeWithoutResult(s -> {
            receipts.begin(actor, COMMAND, key, PAYLOAD);
            firstInserted.countDown();
            await(finishFirst);
            s.setRollbackOnly();
        }));
        firstInserted.await(10, TimeUnit.SECONDS);
        CompletableFuture<Receipt> second = Concurrently.supply(
                () -> tx.execute(s -> receipts.begin(actor, COMMAND, key, PAYLOAD)));
        Thread.sleep(300);
        finishFirst.countDown();
        first.get(10, TimeUnit.SECONDS);

        assertThat(second.get(10, TimeUnit.SECONDS).replay()).isFalse();
    }

    @Test
    void failed_receipt_replays_its_error_code_and_cannot_finish_twice() {
        UUID actor = newAccount();
        String key = key();
        Receipt receipt = tx.execute(s -> {
            Receipt started = receipts.begin(actor, COMMAND, key, PAYLOAD);
            receipts.fail(started.id(), "SCHEDULE_CONFLICT");
            return started;
        });

        Receipt replay = tx.execute(s -> receipts.begin(actor, COMMAND, key, PAYLOAD));

        assertThat(replay.state()).isEqualTo(State.FAILED);
        assertThat(replay.errorCode()).isEqualTo("SCHEDULE_CONFLICT");
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> receipts.succeed(receipt.id(), "CLASS", UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
