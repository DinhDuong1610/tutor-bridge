package com.tutorbridge.platform.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;

/**
 * Instants and dates are read only from ISO-8601 strings, as ADR-0010 and the contract's
 * {@code format: date-time} and {@code format: date} say (#195). Jackson's own readers also take a
 * number or a string of digits as a timestamp ({@code 1700000000} was a moment in 2023,
 * {@code 20261120} a day in year 57443), an array as a date ({@code [2026,11,20]}), and a date-time as
 * a date.
 */
final class IsoDates {

    private IsoDates() {}

    static SimpleModule module() {
        return new SimpleModule("iso-dates")
                .addDeserializer(Instant.class, new Iso<>(Instant.class,
                        text -> OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()))
                .addDeserializer(LocalDate.class, new Iso<>(LocalDate.class,
                        text -> LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)));
    }

    private static final class Iso<T> extends ValueDeserializer<T> {

        private final Class<T> type;
        private final Function<String, T> parse;

        Iso(Class<T> type, Function<String, T> parse) {
            this.type = type;
            this.parse = parse;
        }

        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            // Each failure throws for the ProblemHandler: a 422 VALIDATION_FAILED without parser detail.
            if (!p.hasToken(JsonToken.VALUE_STRING)) {
                return type.cast(ctxt.handleUnexpectedToken(type, p));
            }
            String text = p.getString();
            try {
                return parse.apply(text);
            } catch (DateTimeParseException notIso) {
                return type.cast(ctxt.handleWeirdStringValue(type, text, "not an ISO-8601 value"));
            }
        }

        @Override
        public LogicalType logicalType() {
            return LogicalType.DateTime;
        }
    }
}
