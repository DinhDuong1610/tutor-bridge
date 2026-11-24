package com.tutorbridge.classes.internal;

import com.tutorbridge.classes.internal.MatchingRepository.CandidateRow;
import com.tutorbridge.classes.internal.MatchingRepository.RunRow;
import com.tutorbridge.classes.internal.MatchingRepository.TutorFree;
import com.tutorbridge.classes.internal.MatchingSchedule.Interval;
import com.tutorbridge.identity.api.Parties;
import com.tutorbridge.identity.api.Parties.PublicTutor;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.Cursor;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.Outbox;
import com.tutorbridge.platform.api.Page;
import com.tutorbridge.platform.api.WritePrecondition;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * sendOffer (TX04), getOffer and listOffers (TX06) of UC06, TB-18. A pending offer reserves nothing
 * (INV-13): acceptOffer checks everything again at its commit (TX05).
 */
@Service
public class OfferService {

    /** The {@code Offer} schema of the contract. */
    public record Offer(UUID id, UUID needId, UUID needVersionId, long revision, String state, UUID tutorId,
            String goalSnapshot, int durationMinutes, int frequencyPer7Days, LocalDate cycleStartDate, int rateVnd60,
            int priceVndSession, List<MatchingRunService.Window> schedule, Instant sentAt, Instant expiresAt,
            String reasonCode, UUID classId) {}

    /** {@code OfferInput}; {@code acceptUnchangedTerms} is checked by the controller. */
    public record Input(UUID needVersionId, UUID matchingRunId, UUID tutorId, UUID profileVersionId,
            UUID tutorCalendarVersionId, UUID studentCalendarVersionId, List<MatchingRunService.Window> schedule) {}

    static final String SEND = "SEND_OFFER";
    static final String RESULT_TYPE = "OFFER";
    static final String EXPIRED_REASON = "OFFER_EXPIRED";
    /** BR05: the tutor answers within 48 hours, or before the first session if that comes sooner. */
    static final Duration ANSWER_WINDOW = Duration.ofHours(48);
    private static final String UNIQUE_VIOLATION = "23505";

    private final OfferRepository offers;
    private final NeedRepository needs;
    private final MatchingRepository runs;
    private final ResourceLocks locks;
    private final Parties parties;
    private final CommandReceipts receipts;
    private final Outbox outbox;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    OfferService(OfferRepository offers, NeedRepository needs, MatchingRepository runs, ResourceLocks locks,
            Parties parties, CommandReceipts receipts, Outbox outbox, TransactionOperations transactions, Clock clock,
            IdGenerator ids) {
        this.offers = offers;
        this.needs = needs;
        this.runs = runs;
        this.locks = locks;
        this.parties = parties;
        this.receipts = receipts;
        this.outbox = outbox;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    public Offer send(Actor actor, UUID needId, IdempotencyKey key, WritePrecondition.Expected expected, Input input) {
        requireRole(actor, Actor.Role.PARENT);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("needId", needId.toString());
        payload.put("needVersionId", input.needVersionId().toString());
        payload.put("matchingRunId", input.matchingRunId().toString());
        payload.put("tutorId", input.tutorId().toString());
        payload.put("profileVersionId", input.profileVersionId().toString());
        payload.put("tutorCalendarVersionId", input.tutorCalendarVersionId().toString());
        payload.put("studentCalendarVersionId", input.studentCalendarVersionId().toString());
        payload.put("schedule", input.schedule().stream()
                .map(w -> List.of(w.start().toString(), w.end().toString())).toList());
        payload.put("precondition", expected.version());
        try {
            return transactions.execute(status -> sendInTransaction(actor, needId, key, expected, input, payload));
        } catch (DataAccessException e) {
            // TX04 waits only on the receipt, the need row and the pending offer; past lock_timeout all of
            // it rolled back and the client resends with the same key (ADR-0007, #83).
            if (ResourceLocks.timedOut(e)) {
                throw ResourceLocks.busy();
            }
            // one_pending_offer_per_pair. The need lock makes two sends of one need take turns, so this is the
            // last guard only (AC-UC06-04).
            if (UNIQUE_VIOLATION.equals(sqlState(e))) {
                throw alreadyPending();
            }
            throw e;
        }
    }

    private Offer sendInTransaction(Actor actor, UUID needId, IdempotencyKey key, WritePrecondition.Expected expected,
            Input input, Map<String, Object> payload) {
        locks.limitWait();
        Receipt receipt = receipts.begin(actor.accountId(), SEND, key.value(), payload);
        // TX04: the need first (INV-16), FOR UPDATE as TX05 takes it, so update, confirm and a second send wait.
        NeedRepository.Row need = needs.lock(needId).orElseThrow(OfferService::needNotFound);
        if (!parties.isOwnStudent(actor.accountId(), need.studentId())) {
            throw needNotFound();
        }
        if (receipt.replay()) {
            // INV-22: the owner is checked again above; the answer is the offer as it is now.
            return view(offers.find(receipt.resultId()).orElseThrow());
        }
        if (need.revision() != expected.version()) {
            throw WritePrecondition.stale();
        }
        if (!"CONFIRMED".equals(need.status()) || !need.versionId().equals(input.needVersionId())) {
            throw needVersionChanged();
        }

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        RunRow run = runs.findRun(input.matchingRunId()).filter(r -> r.needId().equals(needId))
                .orElseThrow(OfferService::runNotFound);
        if (!run.needVersionId().equals(need.versionId())) {
            throw needVersionChanged();
        }
        if (!run.expiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "MATCHING_RUN_EXPIRED",
                    "Kết quả gợi ý đã hết hạn. Hãy tìm lại gia sư.");
        }

        // Q2: the offer is a candidate of this run exactly as the parent saw it. A run never changes, so a
        // difference here is a request that was not made from the run.
        CandidateRow candidate = runs.candidates(run.id()).stream()
                .filter(c -> c.tutorId().equals(input.tutorId())).findFirst()
                .orElseThrow(() -> notFromRun("tutorId", "Gia sư không có trong kết quả gợi ý này."));
        if (!candidate.profileId().equals(input.profileVersionId())) {
            throw notFromRun("profileVersionId", "Hồ sơ không khớp với kết quả gợi ý.");
        }
        if (!candidate.tutorCalendarId().equals(input.tutorCalendarVersionId())) {
            throw notFromRun("tutorCalendarVersionId", "Lịch của gia sư không khớp với kết quả gợi ý.");
        }
        if (!run.studentCalendarId().equals(input.studentCalendarVersionId())) {
            throw notFromRun("studentCalendarVersionId", "Lịch của con không khớp với kết quả gợi ý.");
        }
        List<Interval> sessions = runs.sessions(run.id()).getOrDefault(candidate.tutorId(), List.of());
        List<Interval> requested = input.schedule().stream().map(w -> new Interval(w.start(), w.end())).toList();
        if (!sessions.equals(requested)) {
            throw notFromRun("schedule", "Lịch học không khớp với kết quả gợi ý.");
        }

        // Q2: what changed since the run. The tutor is still matchable with the same version, rate and
        // calendar, the price still fits the budget, and the child's calendar is still the effective one.
        int duration = need.draft().durationMinutes();
        PublicTutor tutor = parties.currentPublicTutor(candidate.tutorId())
                .filter(t -> t.profileVersionId().equals(candidate.profileId()))
                .orElseThrow(OfferService::termsChanged);
        int price = MatchingSchedule.sessionPrice(tutor.rateVnd60(), duration);
        if (price != candidate.quotedPrice() || price > need.draft().budgetVndSession()) {
            throw termsChanged();
        }
        if (!offers.effectiveCalendar("TUTOR", candidate.tutorId()).map(candidate.tutorCalendarId()::equals)
                .orElse(false)
                || !offers.effectiveCalendar("STUDENT", need.studentId()).map(run.studentCalendarId()::equals)
                        .orElse(false)) {
            throw termsChanged();
        }

        // Q3, AC-UC06-04: one waiting offer per need and tutor, whatever the key. One already past its
        // deadline is expired here, as the job would (Q1), so it does not block a new one.
        offers.lockPending(needId, candidate.tutorId()).ifPresent(pending -> {
            if (pending.expiresAt().isAfter(now)) {
                throw alreadyPending();
            }
            expire(pending.id(), now);
        });

        // BR04 at send time: every session is still ahead, inside both calendars and free of bookings. Not a
        // reservation: TX05 checks again under the resource locks.
        Interval cycle = MatchingSchedule.cycle(need.draft().cycleStartDate());
        TutorFree free = runs.commonFree(need.studentId(), run.studentCalendarId(), List.of(candidate.tutorId()),
                now, cycle.end()).get(candidate.tutorId());
        boolean allFree = free != null && sessions.stream().allMatch(s -> s.start().isAfter(now)
                && free.free().stream().anyMatch(part -> part.contains(s)));
        if (!allFree) {
            throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT",
                    "Một buổi trong lịch không còn trống. Hãy tìm lại để có lịch mới.");
        }

        UUID offerId = ids.generateId();
        Instant first = sessions.getFirst().start();
        Instant deadline = now.plus(ANSWER_WINDOW).isBefore(first) ? now.plus(ANSWER_WINDOW) : first;
        offers.insert(new OfferRepository.NewOffer(offerId, needId, need.versionId(), candidate.tutorId(),
                candidate.profileId(), candidate.tutorCalendarId(), run.studentCalendarId(), run.id(),
                need.draft().goal(), duration, need.draft().frequencyPer7Days(), need.draft().cycleStartDate(),
                tutor.rateVnd60(), price, now, deadline, sessions));
        // Read by the notifications of TB-21 after commit; ids only (INV-63).
        outbox.publish("OFFER", offerId, "OFFER_SENT", Map.of("offerId", offerId), "OFFER_SENT:" + offerId);
        receipts.succeed(receipt.id(), RESULT_TYPE, offerId);
        return view(offers.find(offerId).orElseThrow());
    }

    public Offer get(Actor actor, UUID offerId) {
        requireParticipant(actor);
        return transactions.execute(status -> {
            OfferRepository.Row row = offers.find(offerId).filter(o -> mayRead(actor, o))
                    .orElseThrow(OfferService::offerNotFound);
            return view(row);
        });
    }

    /** A parent's offers for all their children, or a tutor's received offers, newest first. */
    public Page<Offer> list(Actor actor, String cursor, Integer limit) {
        requireParticipant(actor);
        int size = Page.limit(limit);
        Cursor after = Cursor.decode(cursor);
        return transactions.execute(status -> {
            List<OfferRepository.Row> rows = actor.role() == Actor.Role.TUTOR
                    ? offers.listByTutor(actor.accountId(), after, size + 1)
                    : offers.listByStudents(parties.ownStudentIds(actor.accountId()), after, size + 1);
            Page<OfferRepository.Row> page = Page.of(rows, size, row -> new Cursor(row.sentAt(), row.id()));
            return new Page<>(views(page.items()), page.nextCursor());
        });
    }

    /** Q1: one PENDING offer past its deadline becomes EXPIRED, with the event TB-21 tells the parent from. */
    void expire(UUID offerId, Instant now) {
        if (offers.expire(offerId, now)) {
            outbox.publish("OFFER", offerId, "OFFER_EXPIRED", Map.of("offerId", offerId), "OFFER_EXPIRED:" + offerId);
        }
    }

    private boolean mayRead(Actor actor, OfferRepository.Row offer) {
        return actor.role() == Actor.Role.TUTOR
                ? offer.tutorId().equals(actor.accountId())
                : parties.isOwnStudent(actor.accountId(), offer.studentId());
    }

    private Offer view(OfferRepository.Row row) {
        return views(List.of(row)).getFirst();
    }

    private List<Offer> views(List<OfferRepository.Row> rows) {
        List<UUID> offerIds = rows.stream().map(OfferRepository.Row::id).toList();
        Map<UUID, List<Interval>> sessions = offers.sessions(offerIds);
        Map<UUID, UUID> classes = offers.classes(offerIds);
        Instant now = clock.instant();
        return rows.stream().map(row -> {
            // Q1: past its deadline a PENDING offer is EXPIRED to everyone, before the job writes it.
            boolean overdue = "PENDING".equals(row.state()) && !row.expiresAt().isAfter(now);
            return new Offer(row.id(), row.needId(), row.needVersionId(), row.revision(),
                    overdue ? "EXPIRED" : row.state(), row.tutorId(), row.goalSnapshot(), row.durationMinutes(),
                    row.frequency(), row.cycleStartDate(), row.rateVnd60(), row.priceVndSession(),
                    sessions.getOrDefault(row.id(), List.of()).stream()
                            .map(s -> new MatchingRunService.Window(s.start(), s.end())).toList(),
                    row.sentAt(), row.expiresAt(), overdue ? EXPIRED_REASON : row.reasonCode(),
                    classes.get(row.id()));
        }).toList();
    }

    private static void requireRole(Actor actor, Actor.Role role) {
        if (actor.role() != role) {
            throw accessUnavailable();
        }
    }

    private static void requireParticipant(Actor actor) {
        if (actor.role() != Actor.Role.PARENT && actor.role() != Actor.Role.TUTOR) {
            throw accessUnavailable();
        }
    }

    private static ApiException accessUnavailable() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                "Tài khoản không thể thực hiện thao tác này.");
    }

    private static ApiException needNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NEED_NOT_FOUND", "Không tìm thấy nhu cầu học.");
    }

    private static ApiException runNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MATCHING_RUN_NOT_FOUND", "Không tìm thấy kết quả gợi ý.");
    }

    static ApiException offerNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Không tìm thấy đề nghị.");
    }

    private static ApiException needVersionChanged() {
        return new ApiException(HttpStatus.CONFLICT, "NEED_VERSION_CHANGED",
                "Nhu cầu đã có bản mới từ lúc bạn tìm. Hãy tìm lại từ bản đang hiệu lực.");
    }

    private static ApiException termsChanged() {
        return new ApiException(HttpStatus.CONFLICT, "OFFER_TERMS_CHANGED",
                "Giá, hồ sơ hoặc lịch của gia sư đã đổi từ lúc bạn tìm. Hãy tìm lại trước khi gửi.");
    }

    private static ApiException alreadyPending() {
        return new ApiException(HttpStatus.CONFLICT, "OFFER_ALREADY_PENDING",
                "Bạn đã có một đề nghị đang chờ gia sư này trả lời.");
    }

    private static ApiException notFromRun(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                List.of(new FieldProblem(field, message)));
    }

    private static String sqlState(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
