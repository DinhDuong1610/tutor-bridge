package com.tutorbridge.identity.internal;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.IdGenerator;

/**
 * UC02 for the parent: listStudents, createStudent, getStudentProfile, patchStudent (TB-11).
 *
 * <p>The owner always comes from the session (INV-03). Only the owning parent reads or edits a
 * profile; any other caller, a student session included while student accounts are P1 (Q1 of TB-11),
 * gets the same 404 as an unknown id (INV-06, AC-UC02-02). Writes take an Idempotency-Key whose
 * receipt commits with the profile (INV-20, INV-21); edits need If-Match (INV-23).
 */
@Service
public class StudentProfiles {

    /** The {@code Student} schema of the contract. */
    public record Student(UUID id, String displayName, int grade, long revision, String shortGoal) {}

    /** {@code StudentPatchInput}: a null field is left as it is. */
    public record Changes(String displayName, Integer grade, String shortGoal) {}

    /** R0 segment: grade 8 only (INV-30). V6 lets the database hold 1 to 12. */
    static final int R0_GRADE = 8;
    static final int NAME_MAX = 100;
    static final int GOAL_MAX = 2000;
    static final String CREATE = "CREATE_STUDENT";
    static final String PATCH = "PATCH_STUDENT";
    static final String RESULT_TYPE = "STUDENT";

    private final StudentRepository students;
    private final CommandReceipts receipts;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final IdGenerator ids;

    StudentProfiles(StudentRepository students, CommandReceipts receipts, TransactionOperations transactions,
            Clock clock, IdGenerator ids) {
        this.students = students;
        this.receipts = receipts;
        this.transactions = transactions;
        this.clock = clock;
        this.ids = ids;
    }

    public Page<Student> list(Actor actor, String cursor, Integer requestedLimit) {
        requireParentRole(actor);
        int limit = Page.limit(requestedLimit);
        Cursor after = Cursor.decode(cursor);
        List<StudentRepository.Row> rows = students.listByParent(actor.accountId(), after, limit + 1);
        Page<StudentRepository.Row> page = Page.of(rows, limit, row -> new Cursor(row.createdAt(), row.id()));
        return new Page<>(page.items().stream().map(StudentProfiles::student).toList(), page.nextCursor());
    }

    public Student create(Actor actor, IdempotencyKey key, String displayName, int grade, String shortGoal) {
        requireParentRole(actor);
        String name = displayName.strip();
        validate(name, grade, shortGoal);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("displayName", name);
        payload.put("grade", grade);
        payload.put("shortGoal", shortGoal);
        return transactions.execute(status -> {
            // Verified parents only: an UNVERIFIED account has no session, a LOCKED one is refused here.
            if (!students.lockActiveParent(actor.accountId())) {
                throw accessUnavailable();
            }
            Receipt receipt = receipts.begin(actor.accountId(), CREATE, key.value(), payload);
            if (receipt.replay()) {
                return replay(actor, receipt);
            }
            UUID id = ids.generateId();
            Instant now = clock.instant();
            students.insert(id, actor.accountId(), name, grade, shortGoal, now);
            students.insertGoal(id, 1, shortGoal, actor.accountId(), now);
            receipts.succeed(receipt.id(), RESULT_TYPE, id);
            return new Student(id, name, grade, 1, shortGoal);
        });
    }

    public Student get(Actor actor, UUID studentId) {
        return students.find(studentId)
                .filter(row -> ownedBy(row, actor))
                .map(StudentProfiles::student)
                .orElseThrow(StudentProfiles::notFound);
    }

    /** Never moves the owner. A new goal adds a history row; the old goal stays in history (UC02 step 3). */
    public Student patch(Actor actor, UUID studentId, IdempotencyKey key, WritePrecondition.Expected expected,
            Changes changes) {
        String name = changes.displayName() == null ? null : changes.displayName().strip();
        if (name == null && changes.grade() == null && changes.shortGoal() == null) {
            // StudentPatchInput has minProperties 1.
            throw invalid(List.of());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("studentId", studentId);
        payload.put("expectedRevision", expected.version());
        payload.put("displayName", name);
        payload.put("grade", changes.grade());
        payload.put("shortGoal", changes.shortGoal());
        return transactions.execute(status -> {
            StudentRepository.Row current = students.lock(studentId)
                    .filter(row -> ownedBy(row, actor))
                    .orElseThrow(StudentProfiles::notFound);
            if (!students.lockActiveParent(actor.accountId())) {
                throw accessUnavailable();
            }
            Receipt receipt = receipts.begin(actor.accountId(), PATCH, key.value(), payload);
            if (receipt.replay()) {
                return replay(actor, receipt);
            }
            if (current.revision() != expected.version()) {
                throw WritePrecondition.stale();
            }
            String newName = name == null ? current.displayName() : name;
            int newGrade = changes.grade() == null ? current.grade() : changes.grade();
            String newGoal = changes.shortGoal() == null ? current.shortGoal() : changes.shortGoal();
            validate(newName, newGrade, newGoal);
            long revision = current.revision() + 1;
            students.update(studentId, newName, newGrade, newGoal, revision);
            if (!newGoal.equals(current.shortGoal())) {
                students.insertGoal(studentId, revision, newGoal, actor.accountId(), clock.instant());
            }
            receipts.succeed(receipt.id(), RESULT_TYPE, studentId);
            return new Student(studentId, newName, newGrade, revision, newGoal);
        });
    }

    /** INV-22: a replay rechecks the owner and returns the profile as it is now. */
    private Student replay(Actor actor, Receipt receipt) {
        return students.find(receipt.resultId())
                .filter(row -> ownedBy(row, actor))
                .map(StudentProfiles::student)
                .orElseThrow(StudentProfiles::notFound);
    }

    private static boolean ownedBy(StudentRepository.Row row, Actor actor) {
        return actor.role() == Actor.Role.PARENT && row.parentId().equals(actor.accountId());
    }

    private static void requireParentRole(Actor actor) {
        if (actor.role() != Actor.Role.PARENT) {
            throw accessUnavailable();
        }
    }

    /** Lengths count code points, as the contract's JSON Schema and the varchar columns do. */
    private static void validate(String name, int grade, String goal) {
        List<FieldProblem> problems = new ArrayList<>();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > NAME_MAX) {
            problems.add(new FieldProblem("displayName", "Tên hiển thị cần từ 1 đến 100 ký tự."));
        }
        if (grade != R0_GRADE) {
            problems.add(new FieldProblem("grade", "Hiện chỉ nhận học sinh lớp 8."));
        }
        if (goal.codePointCount(0, goal.length()) > GOAL_MAX) {
            problems.add(new FieldProblem("shortGoal", "Mục tiêu ngắn tối đa 2000 ký tự."));
        }
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
    }

    private static Student student(StudentRepository.Row row) {
        return new Student(row.id(), row.displayName(), row.grade(), row.revision(), row.shortGoal());
    }

    private static ApiException invalid(List<FieldProblem> problems) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                problems);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Không tìm thấy hồ sơ học sinh.");
    }

    private static ApiException accessUnavailable() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                "Tài khoản không thể thực hiện thao tác này.");
    }
}
