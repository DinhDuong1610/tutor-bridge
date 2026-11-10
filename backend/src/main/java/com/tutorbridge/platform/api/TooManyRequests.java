package com.tutorbridge.platform.api;

import java.time.Duration;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * A 429 that tells the client how long to wait. The response carries {@code Retry-After} in whole
 * seconds, rounded up and at least 1, as {@code components.responses.Problem} declares.
 */
public class TooManyRequests extends ApiException {

    private final Duration retryAfter;

    public TooManyRequests(String code, String title, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, title);
        this.retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    /** Whole seconds, rounded up, never below 1. */
    public long retryAfterSeconds() {
        long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
