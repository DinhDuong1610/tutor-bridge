package com.tutorbridge.classes.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tutorbridge.identity.api.Parties;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.Cursor;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.Page;
import com.tutorbridge.platform.api.WritePrecondition;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * UC04 learning needs for the owning parent (TB-16 part 1, FR-04): listNeeds, createNeedDraft,
 * getNeed, updateNeedDraft. Decisions of Đính on #16 (17/11/2026): the schedule is the child's
 * calendar, not windows in the body (Q1); one unconfirmed version per need, edited in place (Q2); the
 * body replaces the whole draft (Q3); at most one need per child that is not CLOSED (Q4).
 *
 * <p>Locks in INV-16 order, matching TX05 ({@link AcceptOfferService} locks the need row, then the
 * resource locks):
 * <ul>
 *   <li>create: receipt, then the child's resource lock {@code STUDENT:<id>} (no need row exists yet
 *       to queue two creates on), then the owner and the open-need check, then the insert;</li>
 *   <li>update: receipt, then the need row FOR UPDATE, then the owner, the revision and the write. No
 *       resource lock: a draft does not touch the calendar, and taking one after the row would invert
 *       TX05's order.</li>
 * </ul>
 * The write and the receipt result commit together (INV-21). No outside I/O inside (INV-18).
 *
 * <p>A child or a need that is not the caller's own, or does not exist, answers the same 404 (INV-06).
 */
@Service
public class NeedService {

    /**
     * The {@code NeedDraftInput} fields; null is empty (Q3) and left out of the answer, since the
     * contract types allow no null there. Windows are refused, never kept (Q1).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Draft(String goal, Integer budgetVndSession, Integer durationMinutes, Integer frequencyPer7Days,
            LocalDate cycleStartDate) {}

    /** The {@code Need} schema of the contract. */
    public record Need(UUID id, UUID studentId, long revision, UUID versionId, String status, Draft draft,
            UUID studentCalendarId) {}

    static final int GOAL_MAX = 2000;
    static final Set<Integer> DURATIONS = Set.of(60, 90, 120);
    static final String CREATE = "CREATE_NEED_DRAFT";
    static final String UPDATE = "UPDATE_NEED_DRAFT";
    private static final String RESULT_TYPE = "NEED";

    private final NeedRepository needs;
    private final ResourceLocks locks;
    private final Parties parties;
    private final CommandReceipts receipts;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    NeedService(NeedRepository needs, ResourceLocks locks, Parties parties, CommandReceipts receipts,
            TransactionOperations transactions, Clock clock, IdGenerator ids) {
        this.needs = needs;
        this.locks = locks;
        this.parties = parties;
        this.receipts = receipts;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    public Page<Need> list(Actor actor, UUID studentId, String cursor, Integer limit) {
        requireParentRole(actor);
        int size = Page.limit(limit);
        Cursor after = Cursor.decode(cursor);
        if (!parties.isOwnStudent(actor.accountId(), studentId)) {
            throw studentNotFound();
        }
        Page<NeedRepository.Row> rows = Page.of(needs.listByStudent(studentId, after, size + 1), size,
                row -> new Cursor(row.createdAt(), row.id()));
        return new Page<>(rows.items().stream().map(NeedService::need).toList(), rows.nextCursor());
    }

    public Need get(Actor actor, UUID needId) {
        requireParentRole(actor);
        NeedRepository.Row row = needs.find(needId).orElseThrow(NeedService::notFound);
        if (!parties.isOwnStudent(actor.accountId(), row.studentId())) {
            throw notFound();
        }
        return need(row);
    }

    public Need create(Actor actor, UUID studentId, IdempotencyKey key, Draft draft, boolean hasWindows) {
        requireParentRole(actor);
        validate(draft, hasWindows);
        Map<String, Object> payload = payload(draft);
        payload.put("studentId", studentId.toString());
        return transactions.execute(status -> {
            Receipt receipt = receipts.begin(actor.accountId(), CREATE, key.value(), payload);
            locks.lock(ResourceLocks.student(studentId));
            if (!parties.isOwnStudent(actor.accountId(), studentId)) {
                throw studentNotFound();
            }
            if (receipt.replay()) {
                // INV-22: the owner is checked again above; the answer is that need as it is now.
                return needs.find(receipt.resultId()).map(NeedService::need).orElseThrow(NeedService::notFound);
            }
            if (needs.hasOpenNeed(studentId)) {
                throw alreadyOpen();
            }
            UUID needId = ids.generateId();
            UUID versionId = ids.generateId();
            needs.insert(needId, versionId, studentId, row(draft), clock.instant());
            receipts.succeed(receipt.id(), RESULT_TYPE, needId);
            return new Need(needId, studentId, 1, versionId, "DRAFT", draft, null);
        });
    }

    public Need update(Actor actor, UUID needId, IdempotencyKey key, WritePrecondition.Expected expected,
            Draft draft, boolean hasWindows) {
        requireParentRole(actor);
        validate(draft, hasWindows);
        Map<String, Object> payload = payload(draft);
        payload.put("needId", needId.toString());
        payload.put("precondition", expected.version());
        return transactions.execute(status -> {
            Receipt receipt = receipts.begin(actor.accountId(), UPDATE, key.value(), payload);
            NeedRepository.Row current = needs.lock(needId).orElseThrow(NeedService::notFound);
            if (!parties.isOwnStudent(actor.accountId(), current.studentId())) {
                throw notFound();
            }
            if (receipt.replay()) {
                // INV-22: the owner is checked again above; the answer is that need as it is now.
                return need(current);
            }
            if (current.revision() != expected.version()) {
                throw WritePrecondition.stale();
            }
            long revision = current.revision() + 1;
            UUID versionId = current.versionId();
            switch (current.status()) {
                case "CLOSED" -> throw closed();
                case "DRAFT" -> needs.updateDraftVersion(versionId, row(draft));
                default -> {
                    // CONFIRMED: the confirmed version stays as it was (UC04); a new one becomes current (Q2).
                    versionId = ids.generateId();
                    needs.insertVersion(versionId, needId, current.version() + 1, row(draft));
                }
            }
            needs.markDraft(needId, versionId, revision);
            receipts.succeed(receipt.id(), RESULT_TYPE, needId);
            return new Need(needId, current.studentId(), revision, versionId, "DRAFT", draft, null);
        });
    }

    /** INV-20: everything the command depends on, windows excluded since a non-empty array is refused. */
    private static Map<String, Object> payload(Draft draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goal", draft.goal());
        payload.put("budgetVndSession", draft.budgetVndSession());
        payload.put("durationMinutes", draft.durationMinutes());
        payload.put("frequencyPer7Days", draft.frequencyPer7Days());
        payload.put("cycleStartDate", draft.cycleStartDate() == null ? null : draft.cycleStartDate().toString());
        return payload;
    }

    /**
     * The draft's own limits (NeedDraftInput, V1 checks). Whether a draft is complete is checked by
     * confirmNeed, not here: an empty draft is a valid draft (Q3). Only the body is read, so a replay
     * meets the same result. Checked before the receipt, so a bad body leaves nothing.
     */
    static void validate(Draft draft, boolean hasWindows) {
        List<FieldProblem> problems = new ArrayList<>();
        if (draft.goal() != null && draft.goal().codePointCount(0, draft.goal().length()) > GOAL_MAX) {
            problems.add(new FieldProblem("goal", "Mục tiêu tối đa 2000 ký tự."));
        }
        if (draft.budgetVndSession() != null && draft.budgetVndSession() < 1) {
            problems.add(new FieldProblem("budgetVndSession", "Ngân sách mỗi buổi phải là số nguyên dương."));
        }
        if (draft.durationMinutes() != null && !DURATIONS.contains(draft.durationMinutes())) {
            problems.add(new FieldProblem("durationMinutes", "Thời lượng chỉ nhận 60, 90 hoặc 120 phút."));
        }
        if (draft.frequencyPer7Days() != null && (draft.frequencyPer7Days() < 1 || draft.frequencyPer7Days() > 3)) {
            problems.add(new FieldProblem("frequencyPer7Days", "Số buổi mỗi 7 ngày từ 1 đến 3."));
        }
        if (hasWindows) {
            problems.add(new FieldProblem("availableWindows",
                    "Lịch của nhu cầu lấy từ lịch rảnh của con, không gửi kèm khoảng thời gian."));
        }
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    problems);
        }
    }

    private static NeedRepository.Draft row(Draft draft) {
        return new NeedRepository.Draft(draft.goal(), draft.budgetVndSession(), draft.durationMinutes(),
                draft.frequencyPer7Days(), draft.cycleStartDate());
    }

    private static Need need(NeedRepository.Row row) {
        NeedRepository.Draft d = row.draft();
        return new Need(row.id(), row.studentId(), row.revision(), row.versionId(), row.status(),
                new Draft(d.goal(), d.budgetVndSession(), d.durationMinutes(), d.frequencyPer7Days(),
                        d.cycleStartDate()),
                row.studentCalendarId());
    }

    private static void requireParentRole(Actor actor) {
        if (actor.role() != Actor.Role.PARENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                    "Tài khoản không thể thực hiện thao tác này.");
        }
    }

    private static ApiException studentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Không tìm thấy hồ sơ học sinh.");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NEED_NOT_FOUND", "Không tìm thấy nhu cầu học.");
    }

    private static ApiException alreadyOpen() {
        return new ApiException(HttpStatus.CONFLICT, "NEED_ALREADY_OPEN",
                "Con đã có một nhu cầu đang mở. Hãy sửa nhu cầu đó thay vì tạo mới.");
    }

    private static ApiException closed() {
        return new ApiException(HttpStatus.CONFLICT, "NEED_CLOSED", "Nhu cầu đã đóng nên không sửa được.");
    }
}
