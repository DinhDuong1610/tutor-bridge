package com.tutorbridge.platform.web;

import java.math.BigDecimal;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.jdk.NumberDeserializers;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.util.AccessPattern;

/**
 * Integer fields read the contract's {@code type: integer} as JSON Schema defines it (§6.1.1): any
 * number whose fraction is zero. {@code 8}, {@code 8.0} and {@code 8e0} are 8; {@code 8.5} is refused
 * instead of being cut to 8, and so is a whole number outside the Java type (#168). Everything else,
 * nulls for primitives included, goes to Jackson's own deserializer unchanged.
 */
final class WholeNumbers {

    private WholeNumbers() {}

    static SimpleModule module() {
        return new SimpleModule("whole-numbers")
                .addDeserializer(Integer.TYPE, new Exact<>(Integer.class,
                        new NumberDeserializers.IntegerDeserializer(Integer.TYPE, 0), BigDecimal::intValueExact))
                .addDeserializer(Integer.class, new Exact<>(Integer.class,
                        new NumberDeserializers.IntegerDeserializer(Integer.class, null), BigDecimal::intValueExact))
                .addDeserializer(Long.TYPE, new Exact<>(Long.class,
                        new NumberDeserializers.LongDeserializer(Long.TYPE, 0L), BigDecimal::longValueExact))
                .addDeserializer(Long.class, new Exact<>(Long.class,
                        new NumberDeserializers.LongDeserializer(Long.class, null), BigDecimal::longValueExact));
    }

    private static final class Exact<T extends Number> extends ValueDeserializer<T> {

        private final Class<T> type;
        private final ValueDeserializer<T> standard;
        private final Function<BigDecimal, T> exact;

        Exact(Class<T> type, ValueDeserializer<T> standard, Function<BigDecimal, T> exact) {
            this.type = type;
            this.standard = standard;
            this.exact = exact;
        }

        @Override
        public T deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            if (!p.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
                return standard.deserialize(p, ctxt);
            }
            BigDecimal value = p.getDecimalValue();
            try {
                return exact.apply(value);
            } catch (ArithmeticException notWhole) {
                // Throws for the ProblemHandler: a 422 VALIDATION_FAILED without parser detail.
                return type.cast(ctxt.handleWeirdNumberValue(type, value, "not a whole number in range"));
            }
        }

        @Override
        public Object getNullValue(DeserializationContext ctxt) {
            return standard.getNullValue(ctxt);
        }

        @Override
        public AccessPattern getNullAccessPattern() {
            return standard.getNullAccessPattern();
        }

        @Override
        public Object getEmptyValue(DeserializationContext ctxt) {
            return standard.getEmptyValue(ctxt);
        }

        @Override
        public LogicalType logicalType() {
            return LogicalType.Integer;
        }
    }
}
