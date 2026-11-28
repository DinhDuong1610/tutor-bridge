package com.tutorbridge.classes.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorbridge.IntegrationTest;
import com.tutorbridge.PostgresTestSupport;
import com.tutorbridge.classes.OfferFixture;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The expiry of pending offers (TB-18 Q1, Đính 25/11/2026 on #18): overdue PENDING offers become EXPIRED
 * with the OFFER_EXPIRED event, a batch per transaction, skipping rows another transaction holds. Real
 * PostgreSQL; the database is shared, so each check looks at its own offers only.
 */
class OfferExpiryTest extends IntegrationTest {

    @Autowired
    OfferExpiry expiry;

    @Autowired
    OfferRepository offers;

    @Autowired
    OfferService service;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    Clock clock;

    @Autowired
    ApplicationContext context;

    private OfferFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new OfferFixture(jdbc, tx);
    }

    private UUID overdue() {
        return fixture.expiredOffer(fixture.need(fixture.student()), fixture.tutor());
    }

    private String stateOf(UUID offer) {
        return jdbc.sql("""
                select state || '/' || coalesce(reason_code, '-') || '/' || revision || '/' || (concluded_at is not null)
                from class_offers where id = ?""").param(offer).query(String.class).single();
    }

    private int expiredEvents(UUID offer) {
        return fixture.count("select count(*) from outbox_events where event_type = 'OFFER_EXPIRED' and aggregate_id = ?",
                offer);
    }

    @Test
    void Q1_an_overdue_pending_offer_expires_once_with_its_event_and_one_still_in_time_stays() {
        UUID late = overdue();
        UUID inTime = fixture.offer(fixture.need(fixture.student()), fixture.tutor());

        assertThat(expiry.runOnce()).isGreaterThanOrEqualTo(1);

        assertThat(stateOf(late)).isEqualTo("EXPIRED/OFFER_EXPIRED/2/true");
        assertThat(expiredEvents(late)).isOne();
        assertThat(stateOf(inTime)).isEqualTo("PENDING/-/1/false");

        // A second run changes nothing and sends no second event.
        expiry.runOnce();
        assertThat(stateOf(late)).isEqualTo("EXPIRED/OFFER_EXPIRED/2/true");
        assertThat(expiredEvents(late)).isOne();
    }

    @Test
    void Q1_TX06_an_offer_another_transaction_holds_is_skipped_without_waiting_and_expired_next_time()
            throws Exception {
        UUID held = overdue();
        try (Connection respond = DriverManager.getConnection(
                PostgresTestSupport.jdbcUrl(), "tutorbridge_app", PostgresTestSupport.APP_PASSWORD)) {
            respond.setAutoCommit(false);
            // What a reject, a cancel or an accept holds while it decides.
            try (var hold = respond.prepareStatement("select 1 from tutorbridge.class_offers where id = ? for update")) {
                hold.setObject(1, held);
                hold.executeQuery();
            }
            // Bounded: a run that waited on the row would fail here instead of hanging.
            CompletableFuture.runAsync(expiry::runOnce).get(10, TimeUnit.SECONDS);
            assertThat(stateOf(held)).isEqualTo("PENDING/-/1/false");
            respond.rollback();
        }

        expiry.runOnce();
        assertThat(stateOf(held)).isEqualTo("EXPIRED/OFFER_EXPIRED/2/true");
    }

    @Test
    void Q1_more_overdue_offers_than_a_batch_all_expire_in_one_run() {
        List<UUID> late = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            late.add(overdue());
        }
        OfferExpiry small = new OfferExpiry(offers, service, tx, clock, 2, 50);

        small.runOnce();

        assertThat(late).allSatisfy(offer -> assertThat(stateOf(offer)).startsWith("EXPIRED/"));
    }

    @Test
    void Q1_only_the_worker_role_schedules_the_job() {
        // Tests run as the API role (tutorbridge.worker.enabled is off), as an API instance does.
        assertThat(context.getBeansOfType(OfferExpiry.Runner.class)).isEmpty();
        assertThat(context.getBeansOfType(OfferExpiry.class)).hasSize(1);
    }
}
