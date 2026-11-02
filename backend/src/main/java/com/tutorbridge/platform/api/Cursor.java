package com.tutorbridge.platform.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Opaque keyset position: the sort time and the id that breaks ties. Authorization stays in the
 * list query, so a forged cursor can only move inside what the caller may already read.
 */
public record Cursor(Instant at, UUID id) {

    private static final String VERSION = "v1";

    public String encode() {
        String raw = VERSION + "|" + ChronoUnit.MICROS.between(Instant.EPOCH, at) + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns null for the first page. */
    public static Cursor decode(String value) {
        if (value == null) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 3 || !parts[0].equals(VERSION)) {
                throw invalid();
            }
            return new Cursor(Instant.EPOCH.plus(Long.parseLong(parts[1]), ChronoUnit.MICROS), UUID.fromString(parts[2]));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CURSOR_INVALID", "Vị trí trang không hợp lệ.");
    }
}
