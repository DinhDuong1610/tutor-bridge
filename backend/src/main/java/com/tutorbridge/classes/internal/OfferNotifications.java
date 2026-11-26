package com.tutorbridge.classes.internal;

import com.tutorbridge.identity.api.Parties;
import com.tutorbridge.platform.api.Notifications;
import com.tutorbridge.platform.api.OutboxHandler;
import com.tutorbridge.platform.api.OutboxQueue.ClaimedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The notifier of TB-21 (UC15, TX30): each offer and class event of the outbox becomes one in-app
 * notification per recipient, as Đính chose on #21 (Q2). The worker calls these outside any transaction
 * and may call them twice; {@link Notifications#deliver} adds nothing the second time (AC-UC15-02).
 *
 * <p>A notification is the event type and its target only: OFFER or CLASS with the id and the version
 * the event is about. A sent offer is at revision 1. A rejected, cancelled or expired offer never changes
 * again, so its stored revision is the one the event made. A new class is at revision 1.
 */
@Configuration(proxyBeanMethods = false)
class OfferNotifications {

    private static final Logger LOG = LoggerFactory.getLogger(OfferNotifications.class);

    /** Who hears of an offer event (Q2). */
    private enum To { TUTOR, PARENT, BOTH }

    /** The parent of the child, if known, and the tutor of one offer or class. */
    private record Recipients(UUID parentId, UUID tutorId) {}

    @Bean
    OutboxHandler offerSentNotifier(OfferRepository offers, Parties parties, Notifications inbox) {
        return offer("OFFER_SENT", To.TUTOR, false, offers, parties, inbox);
    }

    @Bean
    OutboxHandler offerRejectedNotifier(OfferRepository offers, Parties parties, Notifications inbox) {
        return offer("OFFER_REJECTED", To.PARENT, true, offers, parties, inbox);
    }

    /** By the parent (TX06), or by TX05 for the other pending offers of a need that became a class. */
    @Bean
    OutboxHandler offerCancelledNotifier(OfferRepository offers, Parties parties, Notifications inbox) {
        return offer("OFFER_CANCELLED", To.TUTOR, true, offers, parties, inbox);
    }

    @Bean
    OutboxHandler offerExpiredNotifier(OfferRepository offers, Parties parties, Notifications inbox) {
        return offer("OFFER_EXPIRED", To.BOTH, true, offers, parties, inbox);
    }

    @Bean
    OutboxHandler classCreatedNotifier(JdbcClient jdbc, Parties parties, Notifications inbox) {
        return new Handler("CLASS_CREATED", event -> {
            // Published with the class as its aggregate (AcceptOfferService).
            UUID classId = event.aggregateId();
            Optional<Recipients> both = jdbc.sql("select student_id, tutor_id from classes where id = ?").param(classId)
                    .query((rs, n) -> new Recipients(parties.parentOf(rs.getObject("student_id", UUID.class)).orElse(null),
                            rs.getObject("tutor_id", UUID.class)))
                    .optional();
            if (both.isEmpty()) {
                missing(event);
                return;
            }
            for (UUID recipient : recipients(To.BOTH, both.get())) {
                inbox.deliver(recipient, event.id(), event.eventType(), "CLASS", classId, "1");
            }
        });
    }

    private static Handler offer(String type, To to, boolean ended, OfferRepository offers, Parties parties,
            Notifications inbox) {
        return new Handler(type, event -> {
            // Every offer event is published with the offer as its aggregate (OfferService, AcceptOfferService).
            UUID offerId = event.aggregateId();
            Optional<OfferRepository.Row> offer = offers.find(offerId);
            if (offer.isEmpty()) {
                missing(event);
                return;
            }
            OfferRepository.Row row = offer.get();
            String version = ended ? Long.toString(row.revision()) : "1";
            Recipients both = new Recipients(parties.parentOf(row.studentId()).orElse(null), row.tutorId());
            for (UUID recipient : recipients(to, both)) {
                inbox.deliver(recipient, event.id(), event.eventType(), "OFFER", offerId, version);
            }
        });
    }

    private static List<UUID> recipients(To to, Recipients both) {
        List<UUID> recipients = new ArrayList<>();
        if (to != To.PARENT) {
            recipients.add(both.tutorId());
        }
        if (to != To.TUTOR && both.parentId() != null) {
            recipients.add(both.parentId());
        }
        return recipients;
    }

    /**
     * An event about a target that does not exist cannot become a notification; retrying it forever would
     * not help (Q3), so it is logged with ids only (INV-63) and marked delivered.
     */
    private static void missing(ClaimedEvent event) {
        LOG.warn("Outbox event {} of type {} names no existing target; no notification made", event.id(),
                event.eventType());
    }

    @FunctionalInterface
    private interface Body {
        void handle(ClaimedEvent event);
    }

    private record Handler(String eventType, Body body) implements OutboxHandler {
        @Override
        public void handle(ClaimedEvent event) {
            body.handle(event);
        }
    }
}
