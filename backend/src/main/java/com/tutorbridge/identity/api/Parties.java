package com.tutorbridge.identity.api;

import java.util.Optional;
import java.util.UUID;

/** What other modules may ask about accounts and profiles, without reading identity tables. */
public interface Parties {

    boolean isActiveTutor(UUID accountId);

    /**
     * Locks both accounts and the profile against change until the transaction ends, then rechecks
     * them. Returns the parent account, or empty when the agreement can no longer be made.
     */
    Optional<UUID> lockAgreementParties(UUID tutorId, UUID profileId, int rateVnd60, UUID studentId);
}
