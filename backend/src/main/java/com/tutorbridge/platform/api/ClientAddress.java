package com.tutorbridge.platform.api;

/**
 * The address of the person behind a request, as the sign-in throttles count it. Declare it as a controller
 * parameter instead of reading the servlet request's remote address.
 *
 * <p>#252 (Đính, 26/11/2026): the admin web calls the API from its own server, which would make every admin
 * one source. That server sends the browser's address in {@link #HEADER}; the platform believes it only
 * when the request comes from an address in {@code tutorbridge.security.trusted-web-proxies} and the header
 * is one literal IP address. Otherwise, and for the mobile app, which calls the API directly, it is the
 * request's own remote address.
 */
public record ClientAddress(String value) {

    public static final String HEADER = "X-TB-Client-IP";
}
