package com.tutorbridge.platform.web;

import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Last line of defence for JSON logs: masks secrets and personal data that slipped into a log line.
 * Code must still never log them (AC-UC01-04); this only limits the damage of a mistake.
 * Wired by {@code logging.structured.json.customizer} in application.yaml.
 */
public class LogRedaction implements StructuredLoggingJsonMembersCustomizer<Object> {

    private static final String MASK = "[REDACTED]";

    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[^\\s,;\"']+");
    private static final Pattern COOKIE = Pattern.compile("(?i)\\b(tb_session|tb_csrf)=[^\\s,;\"']+");
    /**
     * A key that ends with a secret word, with or without a prefix: {@code password}, {@code currentPassword},
     * {@code new_password}, {@code csrfToken}. A key that only starts with one, such as {@code tokenCount}, is
     * left alone. The value stops at {@code ]} too, the end of a record's {@code toString()} (#197).
     */
    private static final Pattern SECRET_PAIR = Pattern.compile(
            "(?i)\\b([a-z0-9_-]*?(?:password|passwd|token|secret|otp|code_hash|idempotency[-_]key))"
                    + "(\"?\\s*[:=]\\s*\"?)[^\\s,;\"'}&\\]]+");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, LogRedaction::redact));
    }

    static String redact(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String result = BEARER.matcher(value).replaceAll("$1" + MASK);
        result = COOKIE.matcher(result).replaceAll("$1=" + MASK);
        result = SECRET_PAIR.matcher(result).replaceAll("$1$2" + MASK);
        return EMAIL.matcher(result).replaceAll("[EMAIL]");
    }
}
