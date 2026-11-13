package com.tutorbridge.platform.web;

import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/**
 * Plain text console logs for the {@code local} profile, masked by the same rules as the JSON logs
 * ({@link LogRedaction}, Issue #85). The message and the stack trace are both masked; the logger
 * name and the trace id carry no personal data. Wired by {@code logging.structured.format.console}
 * in application-local.yaml.
 */
public class RedactedTextLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.systemDefault());

    private final TargetLengthBasedClassNameAbbreviator loggerNames = new TargetLengthBasedClassNameAbbreviator(40);
    private final ThrowableProxyConverter throwables;

    public RedactedTextLogFormatter(ThrowableProxyConverter throwables) {
        this.throwables = throwables;
    }

    @Override
    public String format(ILoggingEvent event) {
        StringBuilder line = new StringBuilder()
                .append(TIME.format(Instant.ofEpochMilli(event.getTimeStamp())))
                .append(' ')
                .append(String.format("%5s", event.getLevel()))
                .append(" [").append(event.getThreadName()).append("] [");
        String traceId = event.getMDCPropertyMap().get("traceId");
        if (traceId != null) {
            line.append(traceId);
        }
        line.append("] ")
                .append(loggerNames.abbreviate(event.getLoggerName()))
                .append(" : ")
                .append(LogRedaction.redact(event.getFormattedMessage()))
                .append('\n');
        if (event.getThrowableProxy() != null) {
            line.append(LogRedaction.redact(throwables.convert(event)));
        }
        return line.toString();
    }
}
