package com.tutorbridge.classes.internal;

import com.tutorbridge.identity.api.Parties;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.CommandResults;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The results of this module's commands, for the command status lookup (TX16, TB-22): the same owner checks
 * as the reads of those results, made now (INV-22). {@code CLASS} (TX05) is not here yet: its reader comes
 * with getClass in TB-20, so until then its status leaves the result out (Huy, 24/11/2026).
 */
@Component
class ClassesCommandResults implements CommandResults {

    private final NeedRepository needs;
    private final MatchingRepository runs;
    private final CalendarRepository calendars;
    private final Parties parties;

    ClassesCommandResults(NeedRepository needs, MatchingRepository runs, CalendarRepository calendars,
            Parties parties) {
        this.needs = needs;
        this.runs = runs;
        this.calendars = calendars;
        this.parties = parties;
    }

    @Override
    public Set<String> resultTypes() {
        return Set.of(NeedService.RESULT_TYPE, MatchingRunService.RESULT_TYPE, CalendarService.RESULT_TYPE);
    }

    @Override
    public boolean visibleTo(Actor actor, String resultType, UUID resultId) {
        return switch (resultType) {
            case NeedService.RESULT_TYPE -> needs.find(resultId)
                    .map(need -> ownsStudent(actor, need.studentId())).orElse(false);
            case MatchingRunService.RESULT_TYPE -> runs.findRun(resultId)
                    .map(run -> ownsStudent(actor, run.studentId())).orElse(false);
            case CalendarService.RESULT_TYPE -> calendars.findOwner(resultId)
                    .map(owner -> CalendarService.TUTOR.equals(owner.kind())
                            ? actor.role() == Actor.Role.TUTOR && owner.id().equals(actor.accountId())
                            : ownsStudent(actor, owner.id()))
                    .orElse(false);
            default -> false;
        };
    }

    private boolean ownsStudent(Actor actor, UUID studentId) {
        return actor.role() == Actor.Role.PARENT && parties.isOwnStudent(actor.accountId(), studentId);
    }
}
