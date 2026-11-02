package com.tutorbridge.platform.api;

import org.slf4j.MDC;

/** The trace id of the current request, for logs, Problem responses and audit_events.trace_id. */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";

    private TraceContext() {}

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
