package com.tutorbridge.classes.internal;

import com.tutorbridge.classes.api.AcceptOffer;
import com.tutorbridge.identity.api.Parties;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.Outbox;
import com.tutorbridge.platform.api.WritePrecondition;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AcceptOfferService implements AcceptOffer {

    private static final String COMMAND = "ACCEPT_OFFER";
    private static final int MAX_ATTEMPTS = 3;
    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String DEADLOCK = "40P01";
    private static final String SERIALIZATION_FAILURE = "40001";

    private record OfferRef(UUID needId, UUID tutorId) {}

    private record Offer(
            UUID id, UUID needId, UUID needVersionId, UUID tutorId, UUID profileId, long revision, String state,
            String goalSnapshot, int durationMinutes, int rateVnd60, int priceVndSession, Instant sentAt,
            Instant expiresAt) {}

    private record Need(UUID studentId, String status, UUID currentVersionId) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final CommandReceipts receipts;
    private final Outbox outbox;
    private final Parties parties;
    private final ResourceLocks resourceLocks;

    AcceptOfferService(JdbcClient jdbc, TransactionTemplate tx, CommandReceipts receipts, Outbox outbox,
            Parties parties, ResourceLocks resourceLocks) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.receipts = receipts;
        this.outbox = outbox;
        this.parties = parties;
        this.resourceLocks = resourceLocks;
    }

    /**
     * acceptOffer over HTTP (TB-19). Only a tutor accepts, as only the parent cancels (TX06, #235): any other
     * role is 403 before anything is read. A tutor who is not the recipient gets 404 (INV-06).
     */
    public AcceptedClass accept(Actor actor, UUID offerId, WritePrecondition.Expected expected, IdempotencyKey key) {
        if (actor.role() != Actor.Role.TUTOR) {
            throw accessUnavailable();
        }
        return accept(actor.accountId(), offerId, expected.version(), key.value());
    }

    @Override
    public AcceptedClass accept(UUID actorId, UUID offerId, long expectedRevision, String idempotencyKey) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> acceptInTransaction(actorId, offerId, expectedRevision, idempotencyKey));
            } catch (DataAccessException e) {
                String sqlState = sqlState(e);
                if (EXCLUSION_VIOLATION.equals(sqlState)) {
                    throw scheduleConflict();
                }
                if (UNIQUE_VIOLATION.equals(sqlState)) {
                    throw scopeChanged();
                }
                // A row lock after the resource locks waited past lock_timeout (ADR-0007). Not retried:
                // waiting again would exceed the bound; the client resends with the same key.
                if (ResourceLocks.timedOut(e)) {
                    throw ResourceLocks.busy();
                }
                if (!DEADLOCK.equals(sqlState) && !SERIALIZATION_FAILURE.equals(sqlState)) {
                    throw e;
                }
                if (attempt == MAX_ATTEMPTS) {
                    throw ResourceLocks.busy();
                }
            }
        }
    }

    private AcceptedClass acceptInTransaction(UUID actorId, UUID offerId, long expectedRevision, String key) {
        // ADR-0007 for every wait of this transaction, the receipt, the need and the offer included, not only
        // from the resource locks on: a sendOffer holding the need must not keep TX05 waiting without end.
        resourceLocks.limitWait();
        // Read without a lock, so a stranger never waits on someone else's offer (INV-06, as TX06).
        OfferRef ref = jdbc.sql("select need_id, tutor_id from class_offers where id = ?")
                .param(offerId).query(OfferRef.class).optional()
                .filter(offer -> offer.tutorId().equals(actorId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND",
                        "Không tìm thấy đề nghị."));

        Receipt receipt = receipts.begin(actorId, COMMAND, key,
                Map.of("offerId", offerId, "expectedRevision", expectedRevision));
        if (receipt.replay()) {
            // INV-22 under FOR SHARE, after the wait on the receipt: an account locked meanwhile is refused,
            // as in TX06 (Codex, #235 round 2).
            requireActiveTutor(actorId);
            return load(receipt.resultId(), receipt.id());
        }

        Need need = jdbc.sql("select student_id, status, current_version_id from learning_needs where id = ? for update")
                .param(ref.needId()).query(Need.class).single();
        Offer offer = jdbc.sql("""
                select id, need_id, need_version_id, tutor_id, profile_id, revision, state, goal_snapshot,
                       duration_minutes, rate_vnd_60 as rate_vnd60, price_vnd_session, sent_at, expires_at
                from class_offers where id = ? for update
                """).param(offerId).query(Offer.class).single();
        // Need, offer, then the account, under FOR SHARE (INV-16; TX06 takes offer then account too): locked
        // while this waited, the tutor is refused; locked from now on, the lock waits for this answer.
        requireActiveTutor(actorId);
        if ("EXPIRED".equals(offer.state())) {
            throw offerExpired();
        }
        if (!"PENDING".equals(offer.state())) {
            throw scopeChanged();
        }
        if (offer.revision() != expectedRevision) {
            throw new ApiException(HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT",
                    "Đề nghị đã thay đổi. Vui lòng tải lại.");
        }
        if (!"CONFIRMED".equals(need.status()) || !offer.needVersionId().equals(need.currentVersionId())) {
            throw scopeChanged();
        }

        lockResources(need.studentId(), offer.tutorId());

        // Measured after the locks: the wait for them must not extend the deadline.
        boolean expired = jdbc.sql("select clock_timestamp() >= ?")
                .param(java.sql.Timestamp.from(offer.expiresAt())).query(Boolean.class).single();
        if (expired) {
            throw offerExpired();
        }
        UUID parentId = parties.lockAgreementParties(
                offer.tutorId(), offer.profileId(), offer.rateVnd60(), need.studentId())
                .orElseThrow(AcceptOfferService::scopeChanged);
        requireSessionsAvailable(offerId, "STUDENT", need.studentId());
        requireSessionsAvailable(offerId, "TUTOR", offer.tutorId());

        UUID classId = createClass(offer, need.studentId());
        UUID goalId = createGoal(classId, offer, parentId);
        jdbc.sql("""
                insert into lesson_sessions(id, class_id, ordinal, during, state)
                select gen_random_uuid(), ?, ordinal, during, 'SCHEDULED'
                from offer_sessions where offer_id = ? order by ordinal
                """).params(classId, offerId).update();

        jdbc.sql("""
                update class_offers
                set state = 'ACCEPTED', revision = revision + 1, concluded_at = clock_timestamp()
                where id = ?
                """).param(offerId).update();
        List<UUID> competing = jdbc.sql("""
                update class_offers
                set state = 'CANCELLED', revision = revision + 1, concluded_at = clock_timestamp(),
                    reason_code = 'NEED_CLOSED'
                where need_id = ? and state = 'PENDING' and id <> ?
                returning id
                """).params(offer.needId(), offerId).query(UUID.class).list();
        jdbc.sql("update learning_needs set status = 'CLOSED', revision = revision + 1 where id = ?")
                .param(offer.needId()).update();

        outbox.publish("CLASS", classId, "CLASS_CREATED", Map.of("classId", classId, "offerId", offerId),
                "CLASS_CREATED:" + classId);
        for (UUID competingId : competing) {
            outbox.publish("OFFER", competingId, "OFFER_CANCELLED", Map.of("offerId", competingId),
                    "OFFER_CANCELLED:" + competingId);
        }
        receipts.succeed(receipt.id(), "CLASS", classId);

        return new AcceptedClass(classId, need.studentId(), offer.tutorId(), 1, "WAITING", offer.priceVndSession(),
                offer.durationMinutes(), goalId, offer.goalSnapshot(), receipt.id());
    }

    /** ADR-0007: shared with calendar replacement, ascending hash order, 5 second lock_timeout. */
    private void lockResources(UUID studentId, UUID tutorId) {
        resourceLocks.lock(ResourceLocks.student(studentId), ResourceLocks.tutor(tutorId));
    }

    private void requireSessionsAvailable(UUID offerId, String ownerKind, UUID ownerId) {
        Optional<UUID> calendar = jdbc.sql("""
                select id from calendars where owner_kind = ? and owner_id = ? and effective
                """).params(ownerKind, ownerId).query(UUID.class).optional();
        if (calendar.isEmpty()) {
            throw scopeChanged();
        }
        int unavailable = jdbc.sql("""
                select count(*) from offer_sessions s
                where s.offer_id = ?
                  and (not coalesce(
                           (select range_agg(w.during) from availability_windows w
                            where w.calendar_id = ? and w.kind <> 'BLOCKED') @> s.during, false)
                       or exists (select 1 from availability_windows w
                                  where w.calendar_id = ? and w.kind = 'BLOCKED' and w.during && s.during))
                """).params(offerId, calendar.get(), calendar.get()).query(Integer.class).single();
        if (unavailable > 0) {
            throw scheduleConflict();
        }
    }

    private UUID createClass(Offer offer, UUID studentId) {
        UUID classId = UUID.randomUUID();
        jdbc.sql("""
                insert into classes(id, originating_need_id, accepted_offer_id, student_id, tutor_id, status,
                                    duration_minutes, price_vnd_session)
                values (?, ?, ?, ?, ?, 'WAITING', ?, ?)
                """).params(classId, offer.needId(), offer.id(), studentId, offer.tutorId(),
                        offer.durationMinutes(), offer.priceVndSession()).update();
        return classId;
    }

    private UUID createGoal(UUID classId, Offer offer, UUID parentId) {
        UUID goalId = UUID.randomUUID();
        // BR05: sending the offer is the parent's consent to these terms.
        jdbc.sql("""
                insert into class_goal_versions(id, class_id, version, goal, status, proposed_by,
                                                parent_approved_at, tutor_approved_at)
                values (?, ?, 1, ?, 'EFFECTIVE', ?, ?, clock_timestamp())
                """).params(goalId, classId, offer.goalSnapshot(), parentId,
                        java.sql.Timestamp.from(offer.sentAt())).update();
        return goalId;
    }

    private AcceptedClass load(UUID classId, UUID operationId) {
        return jdbc.sql("""
                select c.id, c.student_id, c.tutor_id, c.revision, c.status as state, c.price_vnd_session,
                       c.duration_minutes, g.id as effective_goal_version_id, g.goal, ?::uuid as operation_id
                from classes c
                join class_goal_versions g on g.class_id = c.id and g.status = 'EFFECTIVE'
                where c.id = ?
                """).params(operationId, classId).query(AcceptedClass.class).single();
    }

    private static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private void requireActiveTutor(UUID actorId) {
        if (!parties.lockActiveTutor(actorId)) {
            throw accessUnavailable();
        }
    }

    private static ApiException accessUnavailable() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                "Tài khoản không thể thực hiện thao tác này.");
    }

    private static ApiException scheduleConflict() {
        return new ApiException(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT",
                "Lịch của một buổi không còn phù hợp. Chưa có lớp nào được tạo.");
    }

    private static ApiException scopeChanged() {
        return new ApiException(HttpStatus.CONFLICT, "SCOPE_CHANGED",
                "Điều kiện của đề nghị đã thay đổi. Chưa có lớp nào được tạo.");
    }

    private static ApiException offerExpired() {
        return new ApiException(HttpStatus.CONFLICT, "OFFER_EXPIRED", "Đề nghị đã hết hạn.");
    }
}
