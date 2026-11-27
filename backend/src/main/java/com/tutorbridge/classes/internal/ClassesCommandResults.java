package com.tutorbridge.classes.internal;

import com.tutorbridge.identity.api.Parties;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.CommandResults;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The results of this module's commands, for the command status lookup (TX16, TB-22): the same owner checks
 * as the reads of those results, made now (INV-22). {@code OFFER} (sendOffer, rejectOffer, cancelOffer of TB-18)
 * follows getOffer: the offer's tutor, or the parent of its child. {@code CLASS} (acceptOffer, TX05) follows
 * getClass (ClassService.mayRead): the class's tutor, or the parent who owns its student now.
 */
@Component
class ClassesCommandResults implements CommandResults {

    private final NeedRepository needs;
    private final MatchingRepository runs;
    private final CalendarRepository calendars;
    private final OfferRepository offers;
    private final ClassService classes;
    private final Parties parties;

    ClassesCommandResults(NeedRepository needs, MatchingRepository runs, CalendarRepository calendars,
            OfferRepository offers, ClassService classes, Parties parties) {
        this.needs = needs;
        this.runs = runs;
        this.calendars = calendars;
        this.offers = offers;
        this.classes = classes;
        this.parties = parties;
    }

    @Override
    public Set<String> resultTypes() {
        return Set.of(NeedService.RESULT_TYPE, MatchingRunService.RESULT_TYPE, CalendarService.RESULT_TYPE,
                OfferService.RESULT_TYPE, AcceptOfferService.RESULT_TYPE);
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
            // As getOffer reads it (OfferService.mayRead).
            case OfferService.RESULT_TYPE -> offers.find(resultId)
                    .map(offer -> actor.role() == Actor.Role.TUTOR
                            ? offer.tutorId().equals(actor.accountId())
                            : ownsStudent(actor, offer.studentId()))
                    .orElse(false);
            case AcceptOfferService.RESULT_TYPE -> classes.mayRead(actor, resultId);
            default -> false;
        };
    }

    private boolean ownsStudent(Actor actor, UUID studentId) {
        return actor.role() == Actor.Role.PARENT && parties.isOwnStudent(actor.accountId(), studentId);
    }
}
