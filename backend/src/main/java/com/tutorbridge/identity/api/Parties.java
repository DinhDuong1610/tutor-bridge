package com.tutorbridge.identity.api;

import java.util.Optional;
import java.util.UUID;

/** What other modules may ask about accounts and profiles, without reading identity tables. */
public interface Parties {

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
