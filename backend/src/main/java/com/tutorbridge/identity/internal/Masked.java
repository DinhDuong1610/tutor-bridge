package com.tutorbridge.identity.internal;

/**
 * What a request or response record prints instead of a password, a token or an email. Spring MVC logs
 * the records it reads and writes at DEBUG, so their {@code toString()} must not carry a secret (AC-UC01-04,
 * #197); the log formatters mask secrets again as the last line of defence.
 */
public final class Masked {

    public static final String VALUE = "[REDACTED]";

    private Masked() {}
}
