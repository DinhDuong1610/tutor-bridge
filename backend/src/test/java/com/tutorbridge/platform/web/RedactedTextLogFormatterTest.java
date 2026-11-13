package com.tutorbridge.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.yaml.snakeyaml.Yaml;

/** Local text logs are masked like the JSON logs (Issue #85). Fake values only. */
class RedactedTextLogFormatterTest {

    private static final String FORMATTER = "com.tutorbridge.platform.web.RedactedTextLogFormatter";

    private final LoggerContext context = new LoggerContext();

    private LoggingEvent event(String message, Throwable error) {
        var event = new LoggingEvent("test", context.getLogger("com.tutorbridge.identity.internal.AccountSessions"),
                Level.WARN, message, error, null);
        event.setMDCPropertyMap(Map.of("traceId", "0123456789abcdef0123456789abcdef"));
        return event;
    }

    private RedactedTextLogFormatter formatter() {
        var throwables = new ThrowableProxyConverter();
        throwables.setContext(context);
        throwables.start();
        return new RedactedTextLogFormatter(throwables);
    }

    @Test
    void the_message_is_masked_and_stays_readable_text() {
        String line = formatter().format(event(
                "Login for parent.one@example.test with Bearer tok3n-not-a-secret and password=hunter2", null));

        assertThat(line).contains(" WARN ", "[0123456789abcdef0123456789abcdef]", "AccountSessions : ",
                "[EMAIL]", "Bearer [REDACTED]", "password=[REDACTED]")
                .doesNotContain("parent.one@example.test", "tok3n-not-a-secret", "hunter2")
                .doesNotStartWith("{").endsWith("\n");
    }

    @Test
    void the_stack_trace_is_masked_too() {
        var error = new IllegalStateException("Mail to tutor.two@example.test failed, token=abc123def456");

        String line = formatter().format(event("Sending failed", error));

        assertThat(line).contains("IllegalStateException", "[EMAIL]", "token=[REDACTED]", "at com.tutorbridge")
                .doesNotContain("tutor.two@example.test", "abc123def456");
    }

    @Test
    void an_ordinary_line_is_unchanged() {
        String line = formatter().format(event("Offer 0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10 accepted in 42 ms", null));

        assertThat(line).endsWith(" : Offer 0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10 accepted in 42 ms\n");
    }

    @Test
    void the_local_profile_selects_this_formatter() throws IOException {
        try (InputStream yaml = getClass().getResourceAsStream("/application-local.yaml")) {
            Map<String, Object> root = new Yaml().load(yaml);
            Object console = at(root, "logging", "structured", "format", "console");
            assertThat(console).isEqualTo(FORMATTER);
        }
    }

    @Test
    void spring_boot_builds_the_formatter_from_its_class_name() {
        context.putObject(Environment.class.getName(), new MockEnvironment());
        var encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat(FORMATTER);
        encoder.start();

        String line = new String(encoder.encode(event("Reset link for a@b.test", null)), StandardCharsets.UTF_8);

        assertThat(line).contains(" : Reset link for [EMAIL]").doesNotContain("a@b.test");
    }

    @SuppressWarnings("unchecked")
    private static Object at(Map<String, Object> map, String... path) {
        Object current = map;
        for (String key : path) {
            current = ((Map<String, Object>) current).get(key);
        }
        return current;
    }
}
