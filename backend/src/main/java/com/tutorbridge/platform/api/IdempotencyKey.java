package com.tutorbridge.platform.api;

import org.springframework.http.HttpStatus;

/**
 * The {@code Idempotency-Key} header of a write command. Declare it as a controller parameter; the
 * platform reads and checks the header before the controller runs. Pass {@link #value()} to
 * {@link CommandReceipts#begin}.
 *
 * <p>CR-014: a missing key is 400 {@code IDEMPOTENCY_KEY_REQUIRED}, as in
 * draft-ietf-httpapi-idempotency-key-header; a key outside 16 to 128 characters is 422
 * {@code IDEMPOTENCY_KEY_INVALID}. Public authentication commands declare no key at all.
 */
public record IdempotencyKey(String value) {

    public static final String HEADER = "Idempotency-Key";
    public static final int MIN_LENGTH = 16;
    public static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        check(value);
    }

    /** Throws the problem for a missing or out of range key. */
    static void check(String value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Thiếu khóa gửi lại cho thao tác này.");
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_INVALID",
                    "Khóa gửi lại phải có từ 16 đến 128 ký tự.");
        }
    }
}
