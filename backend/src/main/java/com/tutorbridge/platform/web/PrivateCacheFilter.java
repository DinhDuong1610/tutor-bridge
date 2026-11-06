package com.tutorbridge.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Every API response may carry private data, so no cache may keep it (contract: {@code Cache-Control:
 * private, no-store}). Set before the controller runs, so errors carry it too. A controller that
 * serves public content sets its own header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class PrivateCacheFilter extends OncePerRequestFilter {

    static final String PRIVATE_NO_STORE = "private, no-store";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, PRIVATE_NO_STORE);
        chain.doFilter(request, response);
    }
}
