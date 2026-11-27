package com.tutorbridge.classes;

import static com.tutorbridge.classes.OfferHttpFixture.id;
import static com.tutorbridge.classes.OfferHttpFixture.input;
import static com.tutorbridge.classes.OfferHttpFixture.reason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.HttpTest;
import com.tutorbridge.classes.OfferHttpFixture.Candidate;
import com.tutorbridge.classes.OfferHttpFixture.Scenario;
import com.tutorbridge.classes.OfferHttpFixture.Tutor;
import com.tutorbridge.classes.api.AcceptOffer;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.OutboxHandler;
import com.tutorbridge.platform.api.OutboxQueue.ClaimedEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The notifier of TB-21 (UC15, TX30) on the events the offer flows really publish, with the recipients Đính
 * chose on #21 (Q2). Each test hands its own events to the handlers twice, as an at-least-once worker can,
 * so the shared outbox of other tests is left alone. Fake accounts only.
 */
class OfferNotificationsTest extends HttpTest {

    @Autowired
    TransactionTemplate tx;

    @Autowired
    List<OutboxHandler> handlers;

    @Autowired
    AcceptOffer acceptOffer;

    private OfferHttpFixture fx;
    private Map<String, OutboxHandler> byType;

    @BeforeEach
    void setUp() {
        fx = new OfferHttpFixture(mvc, jdbc, tx, a -> sessions().as(a), this::newActor);
        byType = handlers.stream().collect(Collectors.toMap(OutboxHandler::eventType, Function.identity()));
    }

    /** Every event of this aggregate, handled twice each, as a worker restarted after handling would. */
    private void deliverEventsOf(UUID aggregateId) throws Exception {
        List<ClaimedEvent> events = jdbc.sql("""
                select id, aggregate_type, aggregate_id, event_type, payload::text as payload, dedupe_key, attempts
                from outbox_events where aggregate_id = ? order by created_at
                """).param(aggregateId).query(ClaimedEvent.class).list();
        assertThat(events).isNotEmpty();
        for (ClaimedEvent event : events) {
            OutboxHandler handler = byType.get(event.eventType());
            assertThat(handler).as(event.eventType()).isNotNull();
            handler.handle(event);
            handler.handle(event);
        }
    }

    /** "eventType/targetType/targetVersion" of each notification of {@code actor} about {@code target}. */
    private List<String> inboxOf(Actor actor, UUID target) {
        return jdbc.sql("""
                select template_code || '/' || target_type || '/' || target_version from notifications
                where recipient_id = ? and target_id = ? order by created_at, template_code
                """).params(actor.accountId(), target).query(String.class).list();
    }

    private Actor tutorOf(Scenario s) {
        return s.candidate().tutor().actor();
    }

    @Test
    void Q2_a_sent_offer_tells_the_tutor_once_and_not_the_parent() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = id(fx.send(s).andExpect(status().isCreated()));

        deliverEventsOf(offer);

        assertThat(inboxOf(tutorOf(s), offer)).containsExactly("OFFER_SENT/OFFER/1");
        assertThat(inboxOf(s.parent(), offer)).isEmpty();
    }

    @Test
    void Q2_a_rejected_offer_tells_the_parent_with_the_version_the_reject_made() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = id(fx.send(s).andExpect(status().isCreated()));
        fx.respond(tutorOf(s), "reject", offer, UUID.randomUUID().toString(), "\"1\"", reason("DECLINED"))
                .andExpect(status().isOk());

        deliverEventsOf(offer);

        assertThat(inboxOf(s.parent(), offer)).containsExactly("OFFER_REJECTED/OFFER/2");
        assertThat(inboxOf(tutorOf(s), offer)).containsExactly("OFFER_SENT/OFFER/1");
    }

    @Test
    void Q2_a_cancelled_offer_tells_the_tutor() throws Exception {
        Scenario s = fx.scenario();
        UUID offer = id(fx.send(s).andExpect(status().isCreated()));
        fx.respond(s.parent(), "cancel", offer, UUID.randomUUID().toString(), "\"1\"", reason("PARENT_CANCELLED"))
                .andExpect(status().isOk());

        deliverEventsOf(offer);

        assertThat(inboxOf(tutorOf(s), offer)).containsExactly("OFFER_SENT/OFFER/1", "OFFER_CANCELLED/OFFER/2");
        assertThat(inboxOf(s.parent(), offer)).isEmpty();
    }

    @Test
    void Q2_an_expired_offer_tells_both() throws Exception {
        Scenario s = fx.scenario();
        UUID overdue = fx.overdueOffer(s);
        // The next send expires it on the way (TB-18 Q1), as the job would.
        fx.send(s).andExpect(status().isCreated());

        deliverEventsOf(overdue);

        assertThat(inboxOf(s.parent(), overdue)).containsExactly("OFFER_EXPIRED/OFFER/2");
        assertThat(inboxOf(tutorOf(s), overdue)).containsExactly("OFFER_EXPIRED/OFFER/2");
    }

    @Test
    void Q2_a_new_class_tells_both_and_the_competing_tutor_hears_of_the_cancel() throws Exception {
        Scenario s = fx.scenario();
        UUID accepted = id(fx.send(s).andExpect(status().isCreated()));
        // A second tutor of the same need, found by a later run.
        Tutor other = fx.tutor("Thầy Nam Thử", s.need().start());
        Candidate second = fx.run(s.parent(), s.need(), other);
        UUID competing = id(fx.send(s.parent(), s.need().id(), UUID.randomUUID().toString(), "\"2\"",
                input(s.need(), second)).andExpect(status().isCreated()));

        UUID classId = acceptOffer.accept(tutorOf(s).accountId(), accepted, 1, UUID.randomUUID().toString()).id();
        deliverEventsOf(classId);
        deliverEventsOf(competing);

        assertThat(inboxOf(s.parent(), classId)).containsExactly("CLASS_CREATED/CLASS/1");
        assertThat(inboxOf(tutorOf(s), classId)).containsExactly("CLASS_CREATED/CLASS/1");
        assertThat(inboxOf(other.actor(), competing)).containsExactly("OFFER_SENT/OFFER/1", "OFFER_CANCELLED/OFFER/2");
        assertThat(inboxOf(other.actor(), classId)).isEmpty();
    }

    @Test
    void AC_UC15_01_a_send_that_fails_publishes_nothing_to_notify() throws Exception {
        Scenario s = fx.scenario();
        jdbc.sql("update matching_runs set expires_at = now() - interval '1 second' where id = ?")
                .param(s.candidate().runId()).update();
        int before = fx.count("select count(*) from notifications where recipient_id = ?", tutorOf(s).accountId());

        fx.send(s).andExpect(status().isConflict());

        assertThat(fx.count("""
                select count(*) from outbox_events e join class_offers o on o.id = e.aggregate_id
                where o.need_id = ?""", s.need().id())).isZero();
        assertThat(fx.count("select count(*) from notifications where recipient_id = ?", tutorOf(s).accountId()))
                .isEqualTo(before);
    }

    @Test
    void an_event_about_no_existing_offer_makes_no_notification_and_does_not_fail() throws Exception {
        UUID missing = UUID.randomUUID();
        ClaimedEvent event = new ClaimedEvent(UUID.randomUUID(), "OFFER", missing, "OFFER_SENT",
                "{\"offerId\":\"" + missing + "\"}", "OFFER_SENT:" + missing, 1);

        byType.get("OFFER_SENT").handle(event);

        assertThat(fx.count("select count(*) from notifications where target_id = ?", missing)).isZero();
    }
}
