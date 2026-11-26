package com.tutorbridge.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A read always answers in full: the contract declares no 304 for any operation, and every answer is
 * {@code private, no-store} ({@link PrivateCacheFilter}), so no client keeps a copy to revalidate. Spring
 * would otherwise answer 304 to a GET whose {@code If-None-Match} matches the ETag a controller sets (Huy,
 * 25/11/2026, after Codex's review of #222). The validators are hidden from GET and HEAD only: a write
 * keeps {@code If-None-Match: *} (create only) and {@code If-Match}, which the controllers check.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class NoConditionalGetFilter extends OncePerRequestFilter {

    private static final Set<String> HIDDEN = Set.of(HttpHeaders.IF_NONE_MATCH.toLowerCase(),
            HttpHeaders.IF_MODIFIED_SINCE.toLowerCase());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return hidden(name) ? null : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return hidden(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
            }

            @Override
            public long getDateHeader(String name) {
                return hidden(name) ? -1 : super.getDateHeader(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = Collections.list(super.getHeaderNames());
                names.removeIf(NoConditionalGetFilter::hidden);
                return Collections.enumeration(names);
            }
        }, response);
    }

    private static boolean hidden(String name) {
        return name != null && HIDDEN.contains(name.toLowerCase());
    }
}
