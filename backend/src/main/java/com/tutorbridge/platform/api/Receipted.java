package com.tutorbridge.platform.api;

import java.util.Objects;
import java.util.UUID;

/**
 * The answer of a write that has a {@link CommandReceipts receipt}, with that receipt's id. The controller
 * sends the id as {@code X-Operation-Id} where the contract declares it, so the app can resolve an unknown
 * outcome with getCommandStatus (TX16, INV-24). A replay carries the id of the stored receipt, so it is the
 * same id the first answer carried (#221, Đính 28/11/2026).
 */
public record Receipted<T>(T value, UUID operationId) {

    /** The response header that carries {@link #operationId()}. */
    public static final String HEADER = "X-Operation-Id";

    public Receipted {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(operationId, "operationId");
    }
}
