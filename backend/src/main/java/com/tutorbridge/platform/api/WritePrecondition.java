package com.tutorbridge.platform.api;

import org.springframework.http.HttpStatus;

/**
 * The version condition of a write (G3-D07-06). Declare {@link Expected} as a controller parameter
 * when the operation needs {@code If-Match}; declare {@link WritePrecondition} when it also allows the
 * first creation with {@code If-None-Match: *}. The platform reads the headers; the service compares
 * the version under its row lock.
 */
public sealed interface WritePrecondition {

    /** {@code If-None-Match: *}: the resource must not exist yet. */
    record CreateNew() implements WritePrecondition {}

    /** {@code If-Match: "<version>"}: the resource must still be at this version. */
    record Expected(long version) implements WritePrecondition {

        /** Missing is 428, a tag that can never match is 412. */
        public static Expected fromIfMatch(String ifMatch) {
            if (isBlank(ifMatch)) {
                throw missing();
            }
            return new Expected(Etag.parse(ifMatch));
        }
    }

    /** Exactly one of the two headers must be present, and {@code If-None-Match} only as {@code *}. */
    static WritePrecondition fromHeaders(String ifMatch, String ifNoneMatch) {
        if (isBlank(ifMatch) == isBlank(ifNoneMatch)) {
            throw missing();
        }
        if (isBlank(ifMatch)) {
            if (!"*".equals(ifNoneMatch.strip())) {
                throw missing();
            }
            return new CreateNew();
        }
        return Expected.fromIfMatch(ifMatch);
    }

    static ApiException missing() {
        return new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED",
                "Thiếu phiên bản dữ liệu. Vui lòng tải lại rồi thử lại.");
    }

    static ApiException stale() {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT",
                "Dữ liệu đã thay đổi. Vui lòng tải lại.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
