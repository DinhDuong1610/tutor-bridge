package com.tutorbridge.classes;

import static com.tutorbridge.classes.OfferHttpFixture.id;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.HttpTest;
import com.tutorbridge.classes.OfferHttpFixture.Scenario;
import com.tutorbridge.platform.api.OutboxHandler;
import com.tutorbridge.platform.api.OutboxQueue;
import com.tutorbridge.platform.internal.OutboxDispatcher;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The mechanism behind AC-UC15-04 (Đính, #21, 25/11/2026): with the worker's own loop, its default poll of
 * 2 seconds and the real handlers, a committed send shows in the tutor's inbox within 10 seconds on an idle
 * machine. This is not the NFR10 measurement: the p95 under the NFR10 load is measured with AT16 on the
 * shared test server (OP-33, #239), and no p95 is claimed here.
 */
class OfferNotificationDelayTest extends HttpTest {

    /** {@code tutorbridge.worker.poll-interval} of WorkerRole, and its batch size and lease. */
    private static final Duration POLL = Duration.ofSeconds(2);
    private static final int BATCH = 20;
    private static final Duration LEASE = Duration.ofMinutes(1);

    @Autowired
    TransactionTemplate tx;

    @Autowired
    OutboxQueue queue;

    @Autowired
    List<OutboxHandler> handlers;

    private OfferHttpFixture fx;

    @BeforeEach
    void setUp() {
        fx = new OfferHttpFixture(mvc, jdbc, tx, a -> sessions().as(a), this::newActor);
        // Events other tests left behind would sit ahead of this one in the queue, as in OutboxDispatcherTest.
        jdbc.sql("delete from outbox_events").update();
    }

    @Test
    void AC_UC15_04_the_running_worker_shows_a_sent_offer_to_the_tutor_within_10_seconds() throws Exception {
        Scenario s = fx.scenario();
        OutboxDispatcher dispatcher = new OutboxDispatcher(queue, handlers, BATCH, LEASE);
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        worker.scheduleWithFixedDelay(dispatcher::dispatchOnce, POLL.toMillis(), POLL.toMillis(), TimeUnit.MILLISECONDS);
        try {
            UUID offer = id(fx.send(s).andExpect(status().isCreated()));
            // The 201 comes after the commit: from here the clock of AC-UC15-04 runs.
            long committed = System.nanoTime();

            // Both in one wait (Codex, #237): the dispatcher commits the notification, then marks the event
            // delivered in a second commit, so a check of the second right after seeing the first could race.
            await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).untilAsserted(() -> {
                assertThat(fx.count("select count(*) from notifications where recipient_id = ? and target_id = ?",
                        s.candidate().tutor().id(), offer)).isOne();
                assertThat(fx.count(
                        "select count(*) from outbox_events where aggregate_id = ? and delivered_at is not null",
                        offer)).isOne();
            });

            Duration took = Duration.ofNanos(System.nanoTime() - committed);
            assertThat(took).isLessThan(Duration.ofSeconds(10));
        } finally {
            worker.shutdownNow();
            worker.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
