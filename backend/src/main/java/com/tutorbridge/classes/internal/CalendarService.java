package com.tutorbridge.classes.internal;

import com.tutorbridge.identity.api.Parties;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import com.tutorbridge.platform.api.CommandReceipts;
import com.tutorbridge.platform.api.CommandReceipts.Receipt;
import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.WritePrecondition;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * UC07 availability calendars (TB-15, FR-08): read and replace the tutor's own calendar, and the
 * calendar of a student owned by the calling parent ({@code Parties.isOwnStudent}, INV-82).
 *
 * <p>The client sends finite UTC windows; GS04 spreads the weekly pattern over 56 days (OP-26). Each
 * replacement is a new immutable version. It never touches bookings or sessions (UC07: "Sửa rảnh
 * không hủy buổi đã đặt", INV-15).
 *
 * <p>Transaction, in INV-16 order: receipt, then the resource lock {@code TUTOR:<id>} or
 * {@code STUDENT:<id>} shared with TX05 (ADR-0007, so an acceptance never checks availability
 * against a calendar being replaced), then the owner, then for a new command the date range and the
 * latest version, then the write and the receipt result in the same commit (INV-21). No outside I/O
 * inside it (INV-18).
 *
 * <p>A student that is not the caller's own, or does not exist, answers the same 404 (INV-06).
 */
@Service
public class CalendarService {

    public record Window(Instant start, Instant end, String kind) {}

    /** The {@code EffectiveCalendar} schema of the contract. */
    public record Calendar(UUID id, long version, String timezone, List<Window> windows) {}

    static final String TIMEZONE = "Asia/Ho_Chi_Minh";
    static final ZoneId VIETNAM = ZoneId.of(TIMEZONE);
    /** CalendarInput.windows maxItems. */
    static final int MAX_WINDOWS = 200;
    /** Today plus 56 days of spread pattern (OP-26) and one day of margin, decided by Đính on #15 (Q5). */
    static final int HORIZON_DAYS = 57;
    static final Set<String> KINDS = Set.of("AVAILABLE", "PREFERRED", "BLOCKED");
    static final String REPLACE_TUTOR = "REPLACE_TUTOR_CALENDAR";
    static final String REPLACE_STUDENT = "REPLACE_STUDENT_CALENDAR";
    private static final String TUTOR = "TUTOR";
    private static final String STUDENT = "STUDENT";
    private static final String RESULT_TYPE = "CALENDAR";

    private final CalendarRepository calendars;
    private final ResourceLocks locks;
    private final Parties parties;
    private final CommandReceipts receipts;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    CalendarService(CalendarRepository calendars, ResourceLocks locks, Parties parties, CommandReceipts receipts,
            TransactionOperations transactions, Clock clock, IdGenerator ids) {
        this.calendars = calendars;
        this.locks = locks;
        this.parties = parties;
        this.receipts = receipts;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    /** No calendar yet is 404, so GS04 knows to create the first one with {@code If-None-Match: *}. */
    public Calendar getTutorCalendar(Actor actor) {
        requireTutorRole(actor);
        return calendars.findEffective(TUTOR, actor.accountId())
                .map(this::calendar)
                .orElseThrow(CalendarService::notFound);
    }

    public Calendar replaceTutorCalendar(Actor actor, IdempotencyKey key, WritePrecondition precondition,
            String timezone, List<Window> windows) {
        requireTutorRole(actor);
        UUID tutorId = actor.accountId();
        return replace(new Owner(TUTOR, tutorId, REPLACE_TUTOR, ResourceLocks.tutor(tutorId), () -> {
            if (!parties.isActiveTutor(tutorId)) {
                throw accessUnavailable();
            }
        }), actor, key, precondition, timezone, windows);
    }

    public Calendar getStudentCalendar(Actor actor, UUID studentId) {
        requireParentRole(actor);
        if (!parties.isOwnStudent(actor.accountId(), studentId)) {
            throw studentNotFound();
        }
        return calendars.findEffective(STUDENT, studentId)
                .map(this::calendar)
                .orElseThrow(CalendarService::notFound);
    }

    public Calendar replaceStudentCalendar(Actor actor, UUID studentId, IdempotencyKey key,
            WritePrecondition precondition, String timezone, List<Window> windows) {
        requireParentRole(actor);
        return replace(new Owner(STUDENT, studentId, REPLACE_STUDENT, ResourceLocks.student(studentId), () -> {
            if (!parties.isOwnStudent(actor.accountId(), studentId)) {
                throw studentNotFound();
            }
        }), actor, key, precondition, timezone, windows);
    }

    /** Whose calendar, the receipt command, the TX05 lock, and the owner check made under that lock. */
    private record Owner(String kind, UUID id, String command, String lock, Runnable check) {}

    private Calendar replace(Owner owner, Actor actor, IdempotencyKey key, WritePrecondition precondition,
            String timezone, List<Window> windows) {
        validateShape(timezone, windows);
        Map<String, Object> payload = new LinkedHashMap<>();
        // A child's calendar names the child, so one key reused for a sibling is 409, not a replay. The
        // tutor's payload stays as #152 wrote it (the tutor is already the actor): receipts live at least
        // 7 days, and a changed fingerprint would turn an honest resend after a deploy into 409 (INV-20).
        if (STUDENT.equals(owner.kind())) {
            payload.put("owner", owner.id().toString());
        }
        payload.put("precondition", precondition instanceof WritePrecondition.Expected expected
                ? expected.version() : "*");
        payload.put("timezone", timezone);
        payload.put("windows", windows.stream()
                .map(w -> List.of(w.start().toString(), w.end().toString(), w.kind())).toList());
        return transactions.execute(status -> {
            Receipt receipt = receipts.begin(actor.accountId(), owner.command(), key.value(), payload);
            locks.lock(owner.lock());
            owner.check().run();
            if (receipt.replay()) {
                // INV-22: the owner is checked again; the answer is the version that command wrote.
                return calendars.findVersion(receipt.resultId(), owner.kind(), owner.id())
                        .map(this::calendar)
                        .orElseThrow(CalendarService::notFound);
            }
            // Only a new command is held to today's date: a replay after midnight answers what was written (INV-20).
            requireWithinHorizon(windows);
            Optional<CalendarRepository.Version> latest = calendars.lockLatest(owner.kind(), owner.id());
            requireMatches(precondition, latest);
            UUID id = ids.generateId();
            long version = latest.map(v -> v.version() + 1).orElse(1L);
            List<CalendarRepository.Window> rows = windows.stream()
                    .map(w -> new CalendarRepository.Window(w.start(), w.end(), w.kind())).toList();
            List<UUID> windowIds = windows.stream().map(w -> ids.generateId()).toList();
            calendars.replace(latest, id, owner.kind(), owner.id(), version, rows, windowIds);
            receipts.succeed(receipt.id(), RESULT_TYPE, id);
            return calendar(new CalendarRepository.Version(id, version));
        });
    }

    private static void requireMatches(WritePrecondition precondition, Optional<CalendarRepository.Version> latest) {
        boolean matches = switch (precondition) {
            case WritePrecondition.CreateNew create -> latest.isEmpty();
            case WritePrecondition.Expected expected ->
                    latest.isPresent() && latest.get().version() == expected.version();
        };
        if (!matches) {
            throw WritePrecondition.stale();
        }
    }

    /**
     * AC-UC07-01, the part that does not depend on the date: the timezone, the window count, each kind,
     * and each window ending after it starts. Windows may overlap; BLOCKED wins where they do, as TX05
     * and matching read them (Q5 of #15). Checked before the receipt, so a bad body leaves nothing.
     */
    void validateShape(String timezone, List<Window> windows) {
        List<FieldProblem> problems = new ArrayList<>();
        if (!TIMEZONE.equals(timezone)) {
            problems.add(new FieldProblem("timezone", "Chỉ hỗ trợ múi giờ Asia/Ho_Chi_Minh."));
        }
        if (windows.size() > MAX_WINDOWS) {
            problems.add(new FieldProblem("windows", "Tối đa 200 khoảng thời gian."));
        }
        for (int i = 0; i < windows.size() && problems.size() < 20; i++) {
            Window w = windows.get(i);
            String path = "windows[" + i + "]";
            if (!KINDS.contains(w.kind())) {
                problems.add(new FieldProblem(path + ".kind", "Loại khoảng thời gian không hợp lệ."));
            }
            if (!w.end().isAfter(w.start())) {
                problems.add(new FieldProblem(path + ".end", "Giờ kết thúc phải sau giờ bắt đầu."));
            }
        }
        refuse(problems);
    }

    /**
     * AC-UC07-01, the part that depends on the date: each window lies between the start of today in
     * Asia/Ho_Chi_Minh and {@link #HORIZON_DAYS} days later. Runs inside the transaction after the
     * replay check, so it rolls back with the receipt and a stored result is never refused.
     */
    void requireWithinHorizon(List<Window> windows) {
        LocalDate today = LocalDate.now(clock.withZone(VIETNAM));
        Instant earliest = today.atStartOfDay(VIETNAM).toInstant();
        Instant latest = today.plusDays(HORIZON_DAYS).atStartOfDay(VIETNAM).toInstant();
        List<FieldProblem> problems = new ArrayList<>();
        for (int i = 0; i < windows.size() && problems.size() < 20; i++) {
            Window w = windows.get(i);
            if (w.start().isBefore(earliest) || w.end().isAfter(latest)) {
                problems.add(new FieldProblem("windows[" + i + "]", "Chỉ nhận lịch từ hôm nay đến 57 ngày tới."));
            }
        }
        refuse(problems);
    }

    private static void refuse(List<FieldProblem> problems) {
        if (!problems.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    problems);
        }
    }

    private Calendar calendar(CalendarRepository.Version version) {
        return new Calendar(version.id(), version.version(), TIMEZONE, calendars.windows(version.id()).stream()
                .map(w -> new Window(w.start(), w.end(), w.kind())).toList());
    }

    private static void requireTutorRole(Actor actor) {
        if (actor.role() != Actor.Role.TUTOR) {
            throw accessUnavailable();
        }
    }

    private static void requireParentRole(Actor actor) {
        if (actor.role() != Actor.Role.PARENT) {
            throw accessUnavailable();
        }
    }

    /** The same answer as the identity module gives for a student it cannot show (INV-06). */
    private static ApiException studentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Không tìm thấy hồ sơ học sinh.");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CALENDAR_NOT_FOUND", "Chưa có lịch rảnh.");
    }

    private static ApiException accessUnavailable() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                "Tài khoản không thể thực hiện thao tác này.");
    }
}
