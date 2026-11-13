package com.tutorbridge;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;

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

    /** A task that may throw; the index tells the tasks apart. */
    @FunctionalInterface
    public interface Indexed<T> {
        T call(int index) throws Exception;
    }

    /**
     * Starts {@code tasks} threads that wait on one latch, releases them together and returns their
     * results in index order. Use it to send requests at the same moment, for example many tries
     * against one limit (Issue #141).
     */
    public static <T> List<T> atOnce(int tasks, Indexed<T> task) {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<T>> results = IntStream.range(0, tasks)
                .mapToObj(i -> supply(() -> {
                    try {
                        start.await();
                        return task.call(i);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }))
                .toList();
        start.countDown();
        return results.stream().map(CompletableFuture::join).toList();
    }
}
