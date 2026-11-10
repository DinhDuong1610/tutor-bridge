package com.tutorbridge.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code tutorbridge.identity.mail.*}, read by the worker role only.
 *
 * @param from sender address of account emails
 * @param linkBase prefix of the links in emails, ending in {@code /}: {@code tutorbridge://} on a
 *     developer machine, an https domain with Android App Links once it exists (Q7 of TB-10)
 */
@ConfigurationProperties("tutorbridge.identity.mail")
public record VerificationMailProperties(String from, String linkBase) {}
