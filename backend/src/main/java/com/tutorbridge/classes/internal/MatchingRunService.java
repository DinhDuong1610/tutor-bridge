package com.tutorbridge.classes.internal;

import com.tutorbridge.classes.internal.MatchingRepository.CandidateRow;
import com.tutorbridge.classes.internal.MatchingRepository.NeedVersion;
import com.tutorbridge.classes.internal.MatchingRepository.RunRow;
import com.tutorbridge.classes.internal.MatchingRepository.TutorFree;
import com.tutorbridge.classes.internal.MatchingSchedule.Interval;
import com.tutorbridge.identity.api.Parties;
import com.tutorbridge.identity.api.Parties.PublicTutor;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import com.tutorbridge.platform.api.AuditEvents;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.Receipted;
import com.tutorbridge.platform.api.WritePrecondition;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * createMatchingRun and getMatchingRun (UC05, TX03, TB-17) with Đính's answers on #17 (23/11/2026). The
 * design, written before the code, is in {@code docs/handoff/TB-17.md} ("PR 3 thiết kế").
 *
 * <p>createMatchingRun runs in one transaction: receipt, then the need row FOR SHARE (after the receipt,
 * as {@link NeedService} takes the need row, INV-16), then the owner, the replay, the revision and the
 * confirmed version; then the run, its candidates and their sessions, and the receipt result commit
 * together (INV-21). No resource lock: a suggestion holds nothing (INV-13), and a calendar version never
 * changes once written. Every lock wait is bounded by the 5 second lock_timeout of ADR-0007; past it,
 * all rolls back and the answer is 503 SERVICE_BUSY. No outside I/O inside (INV-18). Mode S answers a FAILED run while OP-11 is open
 * (Q1); mode B needs the parent's explicit choice (Q2, INV-38).
 *
 * <p>A need or a run that is not the caller's own, or does not exist, answers the same 404 (INV-06).
 */
@Service
public class MatchingRunService {

    /** Q5: a run can be used for 24 hours; afterwards PH06 says it is old and no offer is sent from it. */
    static final Duration RUN_LIFETIME = Duration.ofHours(24);
    static final String COMMAND = "CREATE_MATCHING_RUN";
    static final String ALGORITHM_B = "B-v1";
    static final String ALGORITHM_S = "S-v1";
    static final String AI_RANKING_UNAVAILABLE = "AI_RANKING_UNAVAILABLE";
    /** The separate event of a B run (BR03, INV-38). The name is the agent's choice, see #215. */
    static final String FALLBACK_USED = "MATCHING_FALLBACK_USED";
    static final int TOP = 5;
    /** Tutors read per page of {@link Parties#matchableTutors} (INV-81). */
    static final int PAGE = 50;
    static final String RESULT_TYPE = "MATCHING_RUN";
    /** Q6: the name of a verification scope, never its code. R0 has one subject (INV-30). */
    private static final Map<String, String> SCOPE_NAMES = Map.of("MATH8_ALGEBRA", "Toán 8 đại số");

    /** {@code MatchInput}. */
    public record Input(String mode, UUID needVersionId, Boolean fallbackExplicitlyChosen) {}

    public record Window(Instant start, Instant end) {}

    /** {@code TutorPublic}: no contact, no evidence (BR07, INV-60). */
    public record TutorView(UUID tutorId, UUID profileVersionId, String displayName, String description,
            int rateVnd60, int experienceMonths, String experienceLabel, List<String> verificationScopes) {}

    /** {@code Candidate}. */
    public record Candidate(TutorView tutor, int rank, int priceVndSession, List<Window> commonSchedule,
            List<String> reasons, UUID tutorCalendarVersionId) {}

    /** {@code MatchingRun}. */
    public record MatchingRun(UUID id, UUID needVersionId, String mode, String status, Instant expiresAt,
            List<Candidate> candidates, String failureCode, UUID studentCalendarVersionId) {}

    /** A tutor that passed every hard filter, with what is stored for it. */
    private record Passed(PublicTutor tutor, UUID calendarId, int price, List<Interval> sessions, BigDecimal e,
            BigDecimal a, BigDecimal score) {}

    private final MatchingRepository runs;
    private final ResourceLocks locks;
    private final Parties parties;
    private final CommandReceipts receipts;
    private final AuditEvents audit;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    MatchingRunService(MatchingRepository runs, ResourceLocks locks, Parties parties, CommandReceipts receipts,
            AuditEvents audit, TransactionOperations transactions, Clock clock, IdGenerator ids) {
        this.runs = runs;
        this.locks = locks;
        this.parties = parties;
        this.receipts = receipts;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    public Receipted<MatchingRun> create(Actor actor, UUID needId, IdempotencyKey key,
            WritePrecondition.Expected expected, Input input) {
        requireParentRole(actor);
        validate(input);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("needId", needId.toString());
        payload.put("mode", input.mode());
        payload.put("needVersionId", input.needVersionId().toString());
        payload.put("fallbackExplicitlyChosen", input.fallbackExplicitlyChosen());
        payload.put("precondition", expected.version());
        try {
            return transactions.execute(status -> createInTransaction(actor, needId, key, expected, input, payload));
        } catch (DataAccessException e) {
            // TX03: the receipt or the need row waited past lock_timeout (ADR-0007). All of it rolled back;
            // the client resends with the same key (#83). Not retried here: waiting again would pass the bound.
            if (ResourceLocks.timedOut(e)) {
                throw ResourceLocks.busy();
            }
            throw e;
        }
    }

    private Receipted<MatchingRun> createInTransaction(Actor actor, UUID needId, IdempotencyKey key,
            WritePrecondition.Expected expected, Input input, Map<String, Object> payload) {
        // Bounds the waits below, the receipt's and the need row's, as every TX of classes (ADR-0007).
        locks.limitWait();
        Receipt receipt = receipts.begin(actor.accountId(), COMMAND, key.value(), payload);
        NeedVersion need = runs.lockNeedShared(needId).orElseThrow(MatchingRunService::needNotFound);
        if (!parties.isOwnStudent(actor.accountId(), need.studentId())) {
            throw needNotFound();
        }
        if (receipt.replay()) {
            // INV-22: the owner is checked again above; the answer is the stored run.
            return new Receipted<>(read(runs.findRun(receipt.resultId()).orElseThrow()), receipt.id());
        }
        if (need.revision() != expected.version()) {
            throw WritePrecondition.stale();
        }
        if (!"CONFIRMED".equals(need.status()) || !need.versionId().equals(input.needVersionId())) {
            throw versionChanged();
        }
        Instant now = clock.instant();
        UUID runId = ids.generateId();
        if ("S".equals(input.mode())) {
            // Q1: OP-11 is open, so S cannot rank; a FAILED run, not an empty one (AC-UC05-04).
            runs.insertRun(runId, need.versionId(), "S", ALGORITHM_S, "FAILED", AI_RANKING_UNAVAILABLE, now,
                    now.plus(RUN_LIFETIME));
            audit.record(actor.accountId(), receipt.id(), "MATCHING_AI_UNAVAILABLE", "MATCHING_RUN", runId,
                    "FAILED");
        } else {
            runs.insertRun(runId, need.versionId(), "B", ALGORITHM_B, "COMPLETED", null, now,
                    now.plus(RUN_LIFETIME));
            List<Passed> ranked = rank(need, now);
            for (int i = 0; i < ranked.size(); i++) {
                Passed p = ranked.get(i);
                runs.insertCandidate(runId, new CandidateRow(p.tutor().tutorId(), p.tutor().profileVersionId(),
                        p.calendarId(), i + 1, p.e(), p.a(), p.score(), p.price()), p.sessions());
            }
            // BR03, INV-38: B is used only on the parent's choice, and that use is its own event, empty
            // or not. A replay returns above, so it never records a second one.
            audit.record(actor.accountId(), receipt.id(), FALLBACK_USED, "MATCHING_RUN", runId, "COMPLETED");
        }
        receipts.succeed(receipt.id(), RESULT_TYPE, runId);
        // The answer is read back as getMatchingRun reads it, so both always say the same.
        return new Receipted<>(read(runs.findRun(runId).orElseThrow()), receipt.id());
    }

    public MatchingRun get(Actor actor, UUID runId) {
        requireParentRole(actor);
        return transactions.execute(status -> {
            RunRow run = runs.findRun(runId).orElseThrow(MatchingRunService::runNotFound);
            if (!parties.isOwnStudent(actor.accountId(), run.studentId())) {
                throw runNotFound();
            }
            return read(run);
        });
    }

    /**
     * BR01–BR04 and the B score: every hard filter before any score, up to 5 real candidates, nothing
     * relaxed (TX03). Tutors are read a page at a time and only the best 5 so far are kept (INV-81).
     */
    private List<Passed> rank(NeedVersion need, Instant now) {
        int duration = need.durationMinutes();
        int budget = need.budgetVndSession();
        Interval cycle = MatchingSchedule.cycle(need.cycleStartDate());
        Instant from = cycle.start().isAfter(now) ? cycle.start() : now;
        List<Interval> preferred = runs.preferredWindows(need.studentCalendarId(), cycle);
        // ceil(rate × D / 60) <= budget exactly when rate <= floor(budget × 60 / D), so the price filter goes to SQL.
        int maxRate = (int) Math.min(Integer.MAX_VALUE, (long) budget * 60 / duration);
        Comparator<Passed> order = Comparator.comparing(Passed::score).reversed()
                .thenComparing(Comparator.comparing(Passed::e).reversed())
                // PostgreSQL orders uuid by its bytes, which is the order of the lowercase text form.
                .thenComparing(p -> p.tutor().tutorId().toString());
        List<Passed> best = new ArrayList<>();
        UUID after = null;
        while (true) {
            List<PublicTutor> page = parties.matchableTutors(maxRate, after, PAGE);
            Map<UUID, TutorFree> free = runs.commonFree(need.studentId(), need.studentCalendarId(),
                    page.stream().map(PublicTutor::tutorId).toList(), from, cycle.end());
            for (PublicTutor tutor : page) {
                int price = MatchingSchedule.sessionPrice(tutor.rateVnd60(), duration);
                TutorFree common = free.get(tutor.tutorId());
                if (price > budget || common == null) {
                    continue;
                }
                Optional<List<Interval>> sessions = MatchingSchedule.sessions(need.cycleStartDate(), duration,
                        need.frequency(), now, common.free(), preferred);
                if (sessions.isEmpty()) {
                    continue;
                }
                BigDecimal e = MatchingSchedule.e(tutor.experienceMonths());
                BigDecimal a = MatchingSchedule.a(MatchingSchedule.fittingWindows(preferred, common.free(), duration),
                        preferred.size());
                best.add(new Passed(tutor, common.calendarId(), price, sessions.get(), e, a, MatchingSchedule.b(e, a)));
            }
            best.sort(order);
            if (best.size() > TOP) {
                best = new ArrayList<>(best.subList(0, TOP));
            }
            if (page.size() < PAGE) {
                return best;
            }
            after = page.getLast().tutorId();
        }
    }

    /** A stored run as the contract shows it; the profile is the version of the run (Q7). */
    private MatchingRun read(RunRow run) {
        List<CandidateRow> rows = runs.candidates(run.id());
        List<Candidate> candidates = List.of();
        if (!rows.isEmpty()) {
            Map<UUID, PublicTutor> tutors = parties.publicTutorsByProfile(
                    rows.stream().map(CandidateRow::profileId).toList()).stream()
                    .collect(Collectors.toMap(PublicTutor::profileVersionId, Function.identity()));
            Map<UUID, List<Interval>> sessions = runs.sessions(run.id());
            int windows = runs.preferredWindows(run.studentCalendarId(), MatchingSchedule.cycle(run.cycleStartDate()))
                    .size();
            candidates = rows.stream().map(row -> {
                PublicTutor tutor = tutors.get(row.profileId());
                return new Candidate(view(tutor), row.rank(), row.quotedPrice(),
                        sessions.getOrDefault(row.tutorId(), List.of()).stream()
                                .map(s -> new Window(s.start(), s.end())).toList(),
                        reasons(run, row, tutor, windows), row.tutorCalendarId());
            }).toList();
        }
        return new MatchingRun(run.id(), run.needVersionId(), run.mode(), run.status(), run.expiresAt(), candidates,
                run.failureCode(), run.studentCalendarId());
    }

    /**
     * Q6, from stored data only, so a run reads the same every time (INV-37): the price, the experience
     * (self-declared), each verified scope by name, and k/n preferred windows when the student has any.
     */
    static List<String> reasons(RunRow run, CandidateRow row, PublicTutor tutor, int windows) {
        List<String> reasons = new ArrayList<>();
        reasons.add("Giá %s một buổi %d phút (%s/60 phút), trong ngân sách %s/buổi.".formatted(vnd(row.quotedPrice()),
                run.durationMinutes(), vnd(tutor.rateVnd60()), vnd(run.budgetVndSession())));
        reasons.add("Kinh nghiệm %d tháng (tự khai).".formatted(tutor.experienceMonths()));
        for (String scope : tutor.verificationScopes()) {
            String name = SCOPE_NAMES.get(scope);
            if (name != null) {
                reasons.add("Đã xác minh: %s.".formatted(name));
            }
        }
        if (windows > 0) {
            reasons.add("Khớp %d/%d khung giờ ưu tiên.".formatted(MatchingSchedule.fittingFrom(row.a(), windows),
                    windows));
        }
        return List.copyOf(reasons);
    }

    /** "150.000 ₫", as the apps write money. */
    static String vnd(int amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', '.') + " ₫";
    }

    private static TutorView view(PublicTutor tutor) {
        if (tutor == null) {
            throw new IllegalStateException("A stored candidate's profile version is missing");
        }
        return new TutorView(tutor.tutorId(), tutor.profileVersionId(), tutor.displayName(), tutor.description(),
                tutor.rateVnd60(), tutor.experienceMonths(), "SELF_DECLARED", tutor.verificationScopes());
    }

    /** The body alone, before the receipt, so a bad body leaves nothing (Q2, INV-38). */
    static void validate(Input input) {
        if ("B".equals(input.mode()) && !Boolean.TRUE.equals(input.fallbackExplicitlyChosen())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    List.of(new FieldProblem("fallbackExplicitlyChosen",
                            "Cần chọn rõ xem danh sách lọc theo điều kiện (phương án B).")));
        }
    }

    private static void requireParentRole(Actor actor) {
        if (actor.role() != Actor.Role.PARENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                    "Tài khoản không thể thực hiện thao tác này.");
        }
    }

    private static ApiException needNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NEED_NOT_FOUND", "Không tìm thấy nhu cầu học.");
    }

    private static ApiException runNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MATCHING_RUN_NOT_FOUND", "Không tìm thấy kết quả gợi ý.");
    }

    private static ApiException versionChanged() {
        return new ApiException(HttpStatus.CONFLICT, "NEED_VERSION_CHANGED",
                "Nhu cầu chưa được xác nhận hoặc đã đổi phiên bản. Hãy tải lại nhu cầu rồi tìm lại.");
    }
}
