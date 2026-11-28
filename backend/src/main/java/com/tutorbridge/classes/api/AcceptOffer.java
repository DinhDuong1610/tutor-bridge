package com.tutorbridge.classes.api;

import java.util.UUID;

/** TX05: the target tutor accepts an offer unchanged, which creates the class atomically. */
public interface AcceptOffer {

    /** The class TX05 made; {@code studentDisplayName} to its tutor too (OP-25, #97). */
    record AcceptedClass(
            UUID id, UUID studentId, String studentDisplayName, UUID tutorId, long revision, String state,
            int priceVndSession, int durationMinutes, UUID effectiveGoalVersionId, String goal, UUID operationId) {}

    AcceptedClass accept(UUID actorId, UUID offerId, long expectedRevision, String idempotencyKey);
}
