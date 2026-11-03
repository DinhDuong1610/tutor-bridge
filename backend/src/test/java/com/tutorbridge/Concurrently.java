package com.tutorbridge;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Starts every task on its own thread. The common pool has one thread per core minus one, so on a
 * small CI runner a blocked task would stop the next one from starting and no test would overlap.
 */
public final class Concurrently {

    private static final ExecutorService THREADS = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "concurrent-test");
        thread.setDaemon(true);
        return thread;
    });

    private Concurrently() {}

    public static <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, THREADS);
    }

    public static CompletableFuture<Void> run(Runnable task) {
        return CompletableFuture.runAsync(task, THREADS);
    }
}
