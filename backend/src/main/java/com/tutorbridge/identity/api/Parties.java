package com.tutorbridge.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What other modules may ask about accounts and profiles, without reading identity tables. */
public interface Parties {

    /**
     * What parents may see of a tutor before an agreement (the {@code TutorPublic} schema; BR07): no
     * contact, no evidence. {@code verificationScopes} are the VERIFIED decisions of this very version.
     */
    record PublicTutor(UUID tutorId, UUID profileVersionId, String displayName, String description, int rateVnd60,
            int experienceMonths, List<String> verificationScopes, long revision) {}

    /**
     * The first hard filters of BR01 for matching (TB-17): an ACTIVE tutor account with a display name and
     * a current APPROVED profile in R0's subject (INV-30), whose rate is at most {@code maxRateVnd60}. Missing
     * data is not a pass (INV-35). One bounded page ordered by tutor id, after {@code afterTutorId} when given
     * (INV-81); the caller asks again with the last id until a page comes back short.
     */
    List<PublicTutor> matchableTutors(int maxRateVnd60, UUID afterTutorId, int limit);

    /**
     * These profile versions as parents saw them in a stored matching run (getMatchingRun, #17 Q7): the
     * version's own text, rate and VERIFIED scopes, whatever its status is now, with the tutor's display
     * name. At most 5 ids (a run's candidates); an unknown id is left out.
     */
    List<PublicTutor> publicTutorsByProfile(Collection<UUID> profileIds);

    /**
     * This tutor as parents may find them now (sendOffer, TB-18 Q2): the same filters as
     * {@link #matchableTutors} without the rate bound, with the current APPROVED profile version. Empty when
     * the tutor is no longer matchable.
     */
    Optional<PublicTutor> currentPublicTutor(UUID tutorId);

    /** Most children one parent's lists read (listOffers, TB-18 Q6); more is refused, never cut short. */
    int MAX_OWN_STUDENTS = 50;

    /**
     * The students of this parent account while it is an ACTIVE parent, otherwise none. At most
     * {@link #MAX_OWN_STUDENTS} (INV-81): a parent with more fails with an IllegalStateException rather than
     * see part of their offers, until UC02 sets a bound (TB-18 Q6).
     */
    List<UUID> ownStudentIds(UUID parentId);

    /**
     * The parent account of this student, whatever its status, for the inbox (TB-21): a notification is
     * only metadata, and reading its target checks access again. Empty for an unknown student.
     */
    Optional<UUID> parentOf(UUID studentId);

    boolean isActiveTutor(UUID accountId);

    /**
     * Whether this student profile belongs to this parent account, and that account is still an
     * ACTIVE parent ("Verified owning PARENT" in the contract). Another family's student, an unknown
     * id and a parent locked or deleted meanwhile all answer false, so callers give one reply for all
     * of them (INV-06). Used by the classes module for the student's calendar (TB-15, INV-82).
     */
    boolean isOwnStudent(UUID parentId, UUID studentId);

    /**
     * {@link #isOwnStudent} under FOR SHARE on the student and the parent account, so a lock or a deletion
     * of that account waits for the caller's transaction, and one committed while the caller waited for an
     * earlier lock is seen here (TB-18, Codex on #235). Takes the student, then the account, as
     * {@link #lockAgreementParties} does.
     */
    boolean lockOwnStudent(UUID parentId, UUID studentId);

    /** {@link #isActiveTutor} under FOR SHARE on the account, for the same reason as {@link #lockOwnStudent}. */
    boolean lockActiveTutor(UUID tutorId);

    /**
     * Locks both accounts and the profile against change until the transaction ends, then rechecks
     * them. Returns the parent account, or empty when the agreement can no longer be made.
     */
    Optional<UUID> lockAgreementParties(UUID tutorId, UUID profileId, int rateVnd60, UUID studentId);
}
