package com.tutorbridge.platform.web;

import com.tutorbridge.platform.api.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Generated here, never read from the client, so a caller cannot write chosen text into logs. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.put(TraceContext.MDC_KEY, UUID.randomUUID().toString().replace("-", ""));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.MDC_KEY);
        }
    }
}
