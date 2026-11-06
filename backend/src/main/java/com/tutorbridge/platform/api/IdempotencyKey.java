package com.tutorbridge.platform.api;

import org.springframework.http.HttpStatus;

/**
 * The {@code Idempotency-Key} header of a write command. Declare it as a controller parameter; the
 * platform reads and checks the header before the controller runs. Pass {@link #value()} to
 * {@link CommandReceipts#begin}.
 */
public record IdempotencyKey(String value) {

    public static final String HEADER = "Idempotency-Key";
    public static final int MIN_LENGTH = 16;
    public static final int MAX_LENGTH = 128;

    /** A missing or out of range key. Status 422 is provisional, see docs/plan/m0-ban-giao.md. */
    public IdempotencyKey {
        if (!isValid(value)) {
            throw invalid();
        }
    }

    static boolean isValid(String value) {
        return value != null && value.length() >= MIN_LENGTH && value.length() <= MAX_LENGTH;
    }

    static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_INVALID",
                "Khóa gửi lại phải có từ 16 đến 128 ký tự.");
    }
}
