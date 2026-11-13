package com.tutorbridge.identity.api;

import java.util.Optional;
import java.util.UUID;

/** What other modules may ask about accounts and profiles, without reading identity tables. */
public interface Parties {

    boolean isActiveTutor(UUID accountId);

    /**
     * Whether this student profile belongs to this parent account. Another family's student and an
     * unknown id both answer false, so callers give the same reply for both (INV-06). Used by the
     * classes module for the student's calendar (TB-15, INV-82).
     */
    boolean isOwnStudent(UUID parentId, UUID studentId);

    /**
     * Locks both accounts and the profile against change until the transaction ends, then rechecks
     * them. Returns the parent account, or empty when the agreement can no longer be made.
     */
    Optional<UUID> lockAgreementParties(UUID tutorId, UUID profileId, int rateVnd60, UUID studentId);
}
