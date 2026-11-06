package com.tutorbridge.platform.web;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.PublicEndpoints;
import com.tutorbridge.platform.api.SessionResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security, minimal (ADR-0010). It authenticates, blocks requests without a session, adds the
 * standard security headers and answers 401 and 403 as Problems. Relational permissions stay in services.
 *
 * <p>Mobile sends {@code Authorization: Bearer}; the admin web sends the {@code tb_session} cookie
 * (contract {@code bearerAuth}, {@code cookieAuth}). Only cookie writes need CSRF and Origin checks:
 * a browser attaches cookies by itself but never a bearer header.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

    static final String SESSION_COOKIE = "tb_session";
    static final String CSRF_COOKIE = "tb_csrf";
    static final String CSRF_HEADER = "X-CSRF-Token";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Bean
    SecurityFilterChain api(HttpSecurity http, ObjectProvider<SessionResolver> resolvers,
            ObjectProvider<PublicEndpoints> publicEndpoints, ProblemWriter problems,
            @Value("${tutorbridge.security.admin-origins:}") List<String> adminOrigins) {
        // No resolver until TB-08: every protected request is then a 401, never an open door.
        SessionResolver resolver = resolvers.getIfAvailable(() -> token -> Optional.empty());
        RequestMatcher cookieWrite = SecurityConfig::isCookieWrite;

        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieName(CSRF_COOKIE);
        csrfTokens.setHeaderName(CSRF_HEADER);
        csrfTokens.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict").path("/"));

        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                // Keep "private, no-store" from PrivateCacheFilter, which the contract requires.
                .headers(h -> h.cacheControl(c -> c.disable()))
                .csrf(c -> c.spa().csrfTokenRepository(csrfTokens).requireCsrfProtectionMatcher(cookieWrite))
                .addFilterBefore(new OriginFilter(cookieWrite, Set.copyOf(adminOrigins), problems), CsrfFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(new SessionFilter(resolver), AnonymousAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, failure) -> problems.write(response,
                                HttpStatus.UNAUTHORIZED.value(), "AUTHENTICATION_REQUIRED",
                                "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."))
                        .accessDeniedHandler((request, response, denied) -> {
                            if (denied instanceof CsrfException) {
                                problems.write(response, HttpStatus.FORBIDDEN.value(), "CSRF_REJECTED",
                                        "Yêu cầu không hợp lệ. Vui lòng tải lại trang.");
                            } else {
                                problems.write(response, HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED",
                                        "Bạn không có quyền thực hiện thao tác này.");
                            }
                        }))
                .authorizeHttpRequests(a -> {
                    publicEndpoints.orderedStream()
                            .flatMap(p -> p.endpoints().stream())
                            .forEach(e -> a.requestMatchers(e.method(), e.pathPattern()).permitAll());
                    a.anyRequest().authenticated();
                });
        return http.build();
    }

    static boolean isCookieWrite(HttpServletRequest request) {
        return !SAFE_METHODS.contains(request.getMethod())
                && bearerToken(request).isEmpty()
                && sessionCookie(request).isPresent();
    }

    static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.length() <= 7 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        String token = header.substring(7).strip();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    static Optional<String> sessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** Puts the {@link Actor} of a live session into the security context. Bearer wins over cookie. */
    static final class SessionFilter extends OncePerRequestFilter {

        private final SessionResolver resolver;

        SessionFilter(SessionResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            bearerToken(request).or(() -> sessionCookie(request))
                    .flatMap(resolver::resolve)
                    .ifPresent(actor -> SecurityContextHolder.getContext().setAuthentication(new ActorAuthentication(actor)));
            chain.doFilter(request, response);
        }
    }

    /**
     * A cookie write must come from an admin web origin. Origin is checked first; Referer only when a
     * browser left Origin out. Neither means rejection.
     */
    static final class OriginFilter extends OncePerRequestFilter {

        private final RequestMatcher cookieWrite;
        private final Set<String> allowed;
        private final ProblemWriter problems;

        OriginFilter(RequestMatcher cookieWrite, Set<String> allowed, ProblemWriter problems) {
            this.cookieWrite = cookieWrite;
            this.allowed = allowed;
            this.problems = problems;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (cookieWrite.matches(request) && !allowed.contains(sourceOrigin(request).orElse(""))) {
                problems.write(response, HttpStatus.FORBIDDEN.value(), "CSRF_REJECTED",
                        "Yêu cầu không hợp lệ. Vui lòng tải lại trang.");
                return;
            }
            chain.doFilter(request, response);
        }

        private static Optional<String> sourceOrigin(HttpServletRequest request) {
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (origin != null && !origin.isBlank() && !"null".equals(origin)) {
                return Optional.of(origin.strip());
            }
            String referer = request.getHeader(HttpHeaders.REFERER);
            if (referer == null || referer.isBlank()) {
                return Optional.empty();
            }
            try {
                URI uri = URI.create(referer.strip());
                if (uri.getScheme() == null || uri.getHost() == null) {
                    return Optional.empty();
                }
                return Optional.of(uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    /**
     * Loads the deferred CSRF token so the {@code tb_csrf} cookie reaches a browser before its first
     * write. Bearer clients do not need it.
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (bearerToken(request).isEmpty() && request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }

    static final class ActorAuthentication extends AbstractAuthenticationToken {

        private final Actor actor;

        ActorAuthentication(Actor actor) {
            super(AuthorityUtils.createAuthorityList("ROLE_" + actor.role().name()));
            this.actor = actor;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public Actor getPrincipal() {
            return actor;
        }
    }
}
