package com.tutorbridge.platform.api;

import java.util.Optional;

/**
 * Turns a session token into the {@link Actor} behind it. The identity module implements it by
 * looking up the token digest, expiry, revocation and {@code auth_epoch} on every request (ADR-0004,
 * TB-08). Until that exists, no request is authenticated.
 *
 * <p>Empty means the token is unknown, expired or revoked; the caller answers 401 without saying which.
 * The token is a secret: never log it.
 */
@FunctionalInterface
public interface SessionResolver {

    Optional<Actor> resolve(String token);
}
