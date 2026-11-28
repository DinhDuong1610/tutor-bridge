package com.tutorbridge.classes.internal;

import com.tutorbridge.identity.api.Parties;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.Cursor;
import com.tutorbridge.platform.api.Page;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * getClass and listSessions (UC07, TX15, TB-20). Only the class's tutor and the parent who owns its student
 * now read it (AC-UC07-04); anyone else, and an unknown id, get the same 404 (INV-06).
 */
@Service
public class ClassService {

    /** {@code Class}: the agreement as TX05 fixed it, and its effective goal (both required). */
    public record ClassView(UUID id, UUID studentId, UUID tutorId, long revision, String state, int priceVndSession,
            int durationMinutes, UUID effectiveGoalVersionId, String goal) {}

    /** {@code Session}. */
    public record SessionView(UUID id, UUID classId, int ordinal, long revision, String state,
            MatchingRunService.Window during) {}

    private final ClassRepository classes;
    private final Parties parties;
    private final TransactionOperations transactions;

    ClassService(ClassRepository classes, Parties parties, TransactionOperations transactions) {
        this.classes = classes;
        this.parties = parties;
        this.transactions = transactions;
    }

    public ClassView get(Actor actor, UUID classId) {
        requireParticipant(actor);
        return transactions.execute(status -> {
            ClassRepository.Row row = readable(actor, classId);
            if (row.effectiveGoalVersionId() == null) {
                // The contract requires the effective goal, and TX05 writes the first one EFFECTIVE (BR05;
                // one_effective_goal). A class without one breaks that invariant: answer a server error,
                // never a Class without its goal (Codex on #240). Should #19 Q1 make the first goal wait,
                // the contract changes first (contract-change).
                throw new IllegalStateException("Class " + classId + " has no effective goal");
            }
            return new ClassView(row.id(), row.studentId(), row.tutorId(), row.revision(), row.state(),
                    row.priceVndSession(), row.durationMinutes(), row.effectiveGoalVersionId(), row.goal());
        });
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
            return new Page<>(page.items().stream().map(row -> new SessionView(row.id(), row.classId(),
                    row.ordinal(), row.revision(), row.state(), new MatchingRunService.Window(row.start(), row.end())))
                    .toList(), page.nextCursor());
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

    private static ApiException classNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", "Không tìm thấy lớp học.");
    }
}
