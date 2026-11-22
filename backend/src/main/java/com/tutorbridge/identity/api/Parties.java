package com.tutorbridge.identity.api;

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

    boolean isActiveTutor(UUID accountId);

    /**
     * Whether this student profile belongs to this parent account, and that account is still an
     * ACTIVE parent ("Verified owning PARENT" in the contract). Another family's student, an unknown
     * id and a parent locked or deleted meanwhile all answer false, so callers give one reply for all
     * of them (INV-06). Used by the classes module for the student's calendar (TB-15, INV-82).
     */
    boolean isOwnStudent(UUID parentId, UUID studentId);

    /**
     * Locks both accounts and the profile against change until the transaction ends, then rechecks
     * them. Returns the parent account, or empty when the agreement can no longer be made.
     */
    Optional<UUID> lockAgreementParties(UUID tutorId, UUID profileId, int rateVnd60, UUID studentId);
}
