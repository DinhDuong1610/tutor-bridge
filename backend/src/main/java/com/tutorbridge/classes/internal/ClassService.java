package com.tutorbridge.classes.internal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tutorbridge.identity.api.Parties;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import com.tutorbridge.platform.api.Cursor;
import com.tutorbridge.platform.api.Page;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * getClass, listSessions (UC07, TX15, TB-20) and listClasses (OP-27, #97). Only the class's tutor and the parent who owns its student
 * now read it (AC-UC07-04); anyone else, and an unknown id, get the same 404 (INV-06).
 */
@Service
public class ClassService {

    /**
     * {@code Class}: the agreement as TX05 fixed it, its effective goal (both required) and the student's display
     * name, to both parties (OP-25, Đính 27/11/2026 on #97).
     */
    public record ClassView(UUID id, UUID studentId, String studentDisplayName, UUID tutorId, long revision,
            String state, int priceVndSession, int durationMinutes, UUID effectiveGoalVersionId, String goal) {}

    /** {@code ClassListItem}: a class and its next session that has not ended, or null (OP-27). */
    public record ClassListItem(@JsonProperty("class") ClassView classView, SessionView nextSession) {}

    /** {@code Session}. */
    public record SessionView(UUID id, UUID classId, int ordinal, long revision, String state,
            MatchingRunService.Window during) {}

    private final ClassRepository classes;
    private final Parties parties;
    private final TransactionOperations transactions;
    private final Clock clock;

    ClassService(ClassRepository classes, Parties parties, TransactionOperations transactions, Clock clock) {
        this.classes = classes;
        this.parties = parties;
        this.transactions = transactions;
        this.clock = clock;
    }

    public ClassView get(Actor actor, UUID classId) {
        requireParticipant(actor);
        return transactions.execute(status -> {
            ClassRepository.Row row = readable(actor, classId);
            return view(row, parties.studentDisplayNames(List.of(row.studentId())));
        });
    }

    /**
     * listClasses (OP-27, #97): the classes of a parent's children, one child with {@code studentId}; a
     * tutor's own classes. Newest first by (created_at, id); each with its next session at the clock's now.
     * Another family's child and an unknown one answer the same 404 (INV-06, AC-UC02-02).
     */
    public Page<ClassListItem> list(Actor actor, UUID studentId, String cursor, Integer limit) {
        requireParticipant(actor);
        if (actor.role() == Actor.Role.TUTOR && studentId != null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.",
                    List.of(new FieldProblem("studentId", "Chỉ phụ huynh lọc lớp theo con.")));
        }
        int size = Page.limit(limit);
        Cursor after = Cursor.decode(cursor);
        return transactions.execute(status -> {
            List<ClassRepository.Row> rows;
            if (actor.role() == Actor.Role.TUTOR) {
                rows = classes.listByTutor(actor.accountId(), after, size + 1);
            } else if (studentId != null) {
                if (!parties.isOwnStudent(actor.accountId(), studentId)) {
                    throw studentNotFound();
                }
                rows = classes.listByStudents(List.of(studentId), after, size + 1);
            } else {
                rows = classes.listByStudents(parties.ownStudentIds(actor.accountId()), after, size + 1);
            }
            Page<ClassRepository.Row> page = Page.of(rows, size, row -> new Cursor(row.createdAt(), row.id()));
            List<UUID> ids = page.items().stream().map(ClassRepository.Row::id).toList();
            Map<UUID, ClassRepository.SessionRow> next = classes.nextSessions(ids, clock.instant());
            Map<UUID, String> names = parties.studentDisplayNames(
                    page.items().stream().map(ClassRepository.Row::studentId).distinct().toList());
            return new Page<>(page.items().stream().map(row -> new ClassListItem(view(row, names),
                    next.containsKey(row.id()) ? session(next.get(row.id())) : null)).toList(), page.nextCursor());
        });
    }

    private static ClassView view(ClassRepository.Row row, Map<UUID, String> names) {
        if (row.effectiveGoalVersionId() == null) {
            // The contract requires the effective goal, and TX05 writes the first one EFFECTIVE (BR05;
            // one_effective_goal). A class without one breaks that invariant: answer a server error,
            // never a Class without its goal (Codex on #240). Should #19 Q1 make the first goal wait,
            // the contract changes first (contract-change).
            throw new IllegalStateException("Class " + row.id() + " has no effective goal");
        }
        String name = names.get(row.studentId());
        if (name == null) {
            // classes.student_id references students: a class always has its student, and so a name.
            throw new IllegalStateException("Class " + row.id() + " has no student name");
        }
        return new ClassView(row.id(), row.studentId(), name, row.tutorId(), row.revision(), row.state(),
                row.priceVndSession(), row.durationMinutes(), row.effectiveGoalVersionId(), row.goal());
    }

    private static SessionView session(ClassRepository.SessionRow row) {
        return new SessionView(row.id(), row.classId(), row.ordinal(), row.revision(), row.state(),
                new MatchingRunService.Window(row.start(), row.end()));
    }

    /** The class's sessions by start time; the class is checked first, so a cursor reaches nothing else. */
    public Page<SessionView> sessions(Actor actor, UUID classId, String cursor, Integer limit) {
        requireParticipant(actor);
        int size = Page.limit(limit);
        Cursor after = Cursor.decode(cursor);
        return transactions.execute(status -> {
            readable(actor, classId);
            List<ClassRepository.SessionRow> rows = classes.sessions(classId, after, size + 1);
            Page<ClassRepository.SessionRow> page = Page.of(rows, size, row -> new Cursor(row.start(), row.id()));
            return new Page<>(page.items().stream().map(ClassService::session).toList(), page.nextCursor());
        });
    }

    /** Whether this actor reads this class now; also the {@code CLASS} result of getCommandStatus (TB-22). */
    public boolean mayRead(Actor actor, UUID classId) {
        return (actor.role() == Actor.Role.PARENT || actor.role() == Actor.Role.TUTOR)
                && classes.find(classId).filter(row -> isParty(actor, row)).isPresent();
    }

    private ClassRepository.Row readable(Actor actor, UUID classId) {
        return classes.find(classId).filter(row -> isParty(actor, row)).orElseThrow(ClassService::classNotFound);
    }

    /** Checked when read, not kept: a parent who no longer owns the student reads nothing (INV-01). */
    private boolean isParty(Actor actor, ClassRepository.Row row) {
        return actor.role() == Actor.Role.TUTOR
                ? row.tutorId().equals(actor.accountId())
                : parties.isOwnStudent(actor.accountId(), row.studentId());
    }

    private static void requireParticipant(Actor actor) {
        if (actor.role() != Actor.Role.PARENT && actor.role() != Actor.Role.TUTOR) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_UNAVAILABLE",
                    "Tài khoản không thể thực hiện thao tác này.");
        }
    }

    private static ApiException studentNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Không tìm thấy hồ sơ học sinh.");
    }

    private static ApiException classNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", "Không tìm thấy lớp học.");
    }
}
