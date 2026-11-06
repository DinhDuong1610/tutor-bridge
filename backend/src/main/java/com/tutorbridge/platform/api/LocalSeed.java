package com.tutorbridge.platform.api;

/**
 * Fake data for running the app on a developer machine, profile {@code local} only. Each module
 * seeds its own tables (module-map): declare an implementation in the module's {@code internal}
 * package. Seeds run in order of {@code @Order}, each in its own transaction, at every start, so they
 * must be idempotent (fixed ids, {@code on conflict do nothing}).
 *
 * <p>Data must be obviously fake: no real names, emails or child data (AGENTS.md).
 */
@FunctionalInterface
public interface LocalSeed {

    void seed();
}
