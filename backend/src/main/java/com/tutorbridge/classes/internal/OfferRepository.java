package com.tutorbridge.classes.internal;

import com.tutorbridge.classes.internal.MatchingSchedule.Interval;
import com.tutorbridge.platform.api.Cursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code class_offers} and {@code offer_sessions} (TX04, TX06, TB-18), in the classes module (module-map).
 * Terms and sessions never change once written (V2 guards); only state, revision, concluded_at and
 * reason_code move, and only from PENDING.
 */
@Repository
class OfferRepository {

    /** One stored offer with the student of its need, so the caller can check the parent. */
    record Row(UUID id, UUID needId, UUID needVersionId, UUID studentId, UUID tutorId, long revision, String state,
            String goalSnapshot, int durationMinutes, int frequency, LocalDate cycleStartDate, int rateVnd60,
            int priceVndSession, Instant sentAt, Instant expiresAt, String reasonCode) {}

    /** What sendOffer writes; the sessions are written in their order as ordinals 1..n. */
    record NewOffer(UUID id, UUID needId, UUID needVersionId, UUID tutorId, UUID profileId, UUID tutorCalendarId,
            UUID studentCalendarId, UUID runId, String goalSnapshot, int durationMinutes, int frequency,
            LocalDate cycleStartDate, int rateVnd60, int priceVndSession, Instant sentAt, Instant expiresAt,
            List<Interval> sessions) {}

    /** A PENDING offer of one need and tutor, at most one (one_pending_offer_per_pair). */
    record Pending(UUID id, Instant expiresAt) {}

    private static final String SELECT = """
            select o.id, o.need_id, o.need_version_id, n.student_id, o.tutor_id, o.revision, o.state,
                   o.goal_snapshot, o.duration_minutes, o.frequency, o.cycle_start_date, o.rate_vnd_60,
                   o.price_vnd_session, o.sent_at, o.expires_at, o.reason_code
            from class_offers o join learning_needs n on n.id = o.need_id
            """;

    private final JdbcClient jdbc;

    OfferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Row> find(UUID offerId) {
        return jdbc.sql(SELECT + " where o.id = ?").param(offerId).query(OfferRepository::row).optional();
    }

    /** A tutor's received offers, newest first by the keyset (sent_at, id), through V18. */
    List<Row> listByTutor(UUID tutorId, Cursor after, int limit) {
        return list("o.tutor_id = :owner", tutorId, after, limit);
    }

    /**
     * The offers of these students' needs, newest first: the needs through V13, their offers through V18
     * (OfferIndexesMigrationTest checks the plan of this very filter on the joined need).
     */
    List<Row> listByStudents(Collection<UUID> studentIds, Cursor after, int limit) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return list("n.student_id = any(:owner)", studentIds.toArray(UUID[]::new), after, limit);
    }

    private List<Row> list(String owner, Object ownerValue, Cursor after, int limit) {
        String position = after == null ? "" : "and (o.sent_at, o.id) < (:afterAt, :afterId)";
        var query = jdbc.sql(SELECT + """
                where %s %s
                order by o.sent_at desc, o.id desc
                limit :limit
                """.formatted(owner, position)).param("owner", ownerValue).param("limit", limit);
        if (after != null) {
            query = query.param("afterAt", at(after.at())).param("afterId", after.id());
        }
        return query.query(OfferRepository::row).list();
    }

    /** Each offer's sessions in order. */
    Map<UUID, List<Interval>> sessions(Collection<UUID> offerIds) {
        Map<UUID, List<Interval>> byOffer = new LinkedHashMap<>();
        if (offerIds.isEmpty()) {
            return byOffer;
        }
        jdbc.sql("""
                select offer_id, lower(during) as start_at, upper(during) as end_at
                from offer_sessions where offer_id = any(?) order by offer_id, ordinal
                """).param(offerIds.toArray(UUID[]::new)).query((ResultSet rs) -> {
                    byOffer.computeIfAbsent(rs.getObject("offer_id", UUID.class), id -> new ArrayList<>())
                            .add(new Interval(rs.getObject("start_at", OffsetDateTime.class).toInstant(),
                                    rs.getObject("end_at", OffsetDateTime.class).toInstant()));
                });
        return byOffer;
    }

    /** The class each accepted offer made (TX05), by offer. */
    Map<UUID, UUID> classes(Collection<UUID> offerIds) {
        Map<UUID, UUID> byOffer = new LinkedHashMap<>();
        if (offerIds.isEmpty()) {
            return byOffer;
        }
        jdbc.sql("select accepted_offer_id, id from classes where accepted_offer_id = any(?)")
                .param(offerIds.toArray(UUID[]::new))
                .query((ResultSet rs) -> {
                    byOffer.put(rs.getObject("accepted_offer_id", UUID.class), rs.getObject("id", UUID.class));
                });
        return byOffer;
    }

    /**
     * The PENDING offer of this need and tutor under FOR UPDATE. The need row is locked first (INV-16, as
     * TX05 takes need then offer), so two sends of one need wait on the need, not here.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Pending> lockPending(UUID needId, UUID tutorId) {
        return jdbc.sql("""
                select id, expires_at from class_offers
                where need_id = ? and tutor_id = ? and state = 'PENDING'
                for update
                """).params(needId, tutorId).query((rs, n) -> new Pending(rs.getObject("id", UUID.class),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    /**
     * The offer row under FOR UPDATE for TX06, then read again with its need. Only the offer is locked: TX05
     * takes the need and then the offer, so waiting on the offer alone cannot close a cycle with it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Row> lock(UUID offerId) {
        boolean exists = jdbc.sql("select 1 from class_offers where id = ? for update").param(offerId)
                .query((rs, n) -> 1).optional().isPresent();
        return exists ? find(offerId) : Optional.empty();
    }

    /** TX06: a PENDING offer ends as REJECTED or CANCELLED with its reason. False when it was no longer PENDING. */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean conclude(UUID offerId, String state, String reasonCode, Instant now) {
        return jdbc.sql("""
                update class_offers
                set state = ?, revision = revision + 1, concluded_at = ?, reason_code = ?
                where id = ? and state = 'PENDING'
                """).params(state, at(now), reasonCode, offerId).update() == 1;
    }

    /**
     * Up to {@code limit} PENDING offers past their deadline, oldest deadline first, locked. Rows another
     * transaction holds (a reject, a cancel, an accept) are skipped; the next run finds them if still due.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    List<UUID> lockOverdue(Instant now, int limit) {
        return jdbc.sql("""
                select id from class_offers
                where state = 'PENDING' and expires_at <= ?
                order by expires_at
                limit ?
                for update skip locked
                """).params(at(now), limit).query(UUID.class).list();
    }

    /** A PENDING offer past its deadline becomes EXPIRED (TB-18 Q1). False when it was no longer PENDING. */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean expire(UUID offerId, Instant now) {
        return jdbc.sql("""
                update class_offers
                set state = 'EXPIRED', revision = revision + 1, concluded_at = ?, reason_code = 'OFFER_EXPIRED'
                where id = ? and state = 'PENDING' and expires_at <= ?
                """).params(at(now), offerId, at(now)).update() == 1;
    }

    /** The effective calendar of a student or tutor, if any. */
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<UUID> effectiveCalendar(String ownerKind, UUID ownerId) {
        return jdbc.sql("select id from calendars where owner_kind = ? and owner_id = ? and effective")
                .params(ownerKind, ownerId).query(UUID.class).optional();
    }

    /** The offer and its sessions; the deferred V2 triggers check the whole schedule at commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    void insert(NewOffer offer) {
        jdbc.sql("""
                insert into class_offers(id, need_id, need_version_id, tutor_id, profile_id, tutor_calendar_id,
                                         student_calendar_id, run_id, state, goal_snapshot, duration_minutes,
                                         frequency, cycle_start_date, rate_vnd_60, price_vnd_session, sent_at,
                                         expires_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(offer.id(), offer.needId(), offer.needVersionId(), offer.tutorId(), offer.profileId(),
                        offer.tutorCalendarId(), offer.studentCalendarId(), offer.runId(), offer.goalSnapshot(),
                        offer.durationMinutes(), offer.frequency(), offer.cycleStartDate(), offer.rateVnd60(),
                        offer.priceVndSession(), at(offer.sentAt()), at(offer.expiresAt()))
                .update();
        List<Interval> sessions = offer.sessions();
        for (int i = 0; i < sessions.size(); i++) {
            jdbc.sql("insert into offer_sessions(offer_id, ordinal, during) values (?, ?, tstzrange(?, ?, '[)'))")
                    .params(offer.id(), i + 1, at(sessions.get(i).start()), at(sessions.get(i).end())).update();
        }
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("need_id", UUID.class),
                rs.getObject("need_version_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getObject("tutor_id", UUID.class), rs.getLong("revision"), rs.getString("state"),
                rs.getString("goal_snapshot"), rs.getInt("duration_minutes"), rs.getInt("frequency"),
                rs.getObject("cycle_start_date", LocalDate.class), rs.getInt("rate_vnd_60"),
                rs.getInt("price_vnd_session"), rs.getObject("sent_at", OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant(), rs.getString("reason_code"));
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
