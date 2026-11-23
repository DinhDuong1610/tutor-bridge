package com.tutorbridge.classes.internal;

import com.tutorbridge.platform.api.ApiException;
import java.sql.SQLException;
import java.util.Arrays;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resource locks of ADR-0007: one transaction-scoped advisory lock per {@code TUTOR:<id>} or
 * {@code STUDENT:<id>}, taken in ascending hash order so two transactions never wait on each other
 * in opposite order, even when two resources share a hash. Every transaction that books, moves or
 * cancels a session, or replaces a calendar, takes them at the "resources by stable key" step of
 * INV-16. A wait longer than {@link #LOCK_TIMEOUT} answers 503 SERVICE_BUSY, which the client
 * treats as an unknown outcome and resends with the same key (#83).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class ResourceLocks {

    static final String LOCK_TIMEOUT = "5s";
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private final JdbcClient jdbc;

    ResourceLocks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static String tutor(Object id) {
        return "TUTOR:" + id;
    }

    static String student(Object id) {
        return "STUDENT:" + id;
    }

    /**
     * Takes the locks, with {@link #LOCK_TIMEOUT} for the rest of the transaction. A statement after this
     * that locks a row another module may hold (as TX05 does with {@code students} and {@code accounts})
     * can time out too: the caller then answers {@link #busy()} for {@link #timedOut}, as
     * {@code AcceptOfferService} does (#160). Rows only locked under the same resource lock, such as a
     * calendar version or a need, cannot make it wait.
     */
    void lock(String... resources) {
        limitWait();
        long[] keys = Arrays.stream(resources).distinct()
                .mapToLong(resource -> jdbc.sql("select hashtextextended(?, 0)").param(resource)
                        .query(Long.class).single())
                .sorted().distinct().toArray();
        try {
            for (long key : keys) {
                jdbc.sql("select pg_advisory_xact_lock(?)").param(key).query((rs, row) -> 1).single();
            }
        } catch (DataAccessException e) {
            if (timedOut(e)) {
                throw busy();
            }
            throw e;
        }
    }

    /**
     * {@link #LOCK_TIMEOUT} for every lock wait in the rest of the transaction, without a resource lock.
     * For a transaction that only waits on rows, as createMatchingRun on its need (TX03): the caller
     * answers {@link #busy()} for {@link #timedOut}, as after {@link #lock}.
     */
    void limitWait() {
        jdbc.sql("set local lock_timeout = '" + LOCK_TIMEOUT + "'").update();
    }

    /**
     * Whether a statement gave up waiting for a lock. The timeout set by {@link #lock} lasts until the
     * transaction ends, so a row lock taken after the resource locks can time out as well (#160); the
     * caller answers {@link #busy()} for it too, as ADR-0007 says.
     */
    static boolean timedOut(Throwable e) {
        return LOCK_NOT_AVAILABLE.equals(sqlState(e));
    }

    /** 503 SERVICE_BUSY: the client treats it as an unknown outcome and resends with the same key (#83). */
    static ApiException busy() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_BUSY",
                "Hệ thống đang bận. Vui lòng gửi lại cùng thao tác.");
    }

    private static String sqlState(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
