package com.tutorbridge.platform.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is calling, taken from a live session (ADR-0004, ADR-0010). Controllers receive it with
 * {@code @AuthenticationPrincipal Actor actor} and pass it to the service; services never read the
 * request or the security context. Relational permissions, such as parent and child, are checked in
 * the service against the database, not here.
 */
public record Actor(UUID accountId, Role role, UUID sessionId) {

    /** The {@code accounts.role} values of V1. */
    public enum Role { PARENT, TUTOR, STUDENT, ADMIN }

    public Actor {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(sessionId, "sessionId");
    }
}
