package com.tutorbridge.platform.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.TestSessions;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.PublicEndpoints;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.Probe.class)
@Import({SecurityConfigTest.Probe.class, WebSecurityTestConfig.class, SecurityConfigTest.OpenProbe.class})
@TestPropertySource(properties = "tutorbridge.security.admin-origins=https://admin.test")
class SecurityConfigTest {

    private static final String ADMIN_ORIGIN = "https://admin.test";
    private static final String CSRF = "csrf-token-value-1234";

    @RestController
    static class Probe {

        @GetMapping("/probe/me")
        Map<String, String> me(@AuthenticationPrincipal Actor actor) {
            return Map.of("accountId", actor.accountId().toString(), "role", actor.role().name());
        }

        @PostMapping("/probe/me")
        Map<String, String> write(@AuthenticationPrincipal Actor actor) {
            return Map.of("accountId", actor.accountId().toString());
        }

        @GetMapping("/probe/public")
        Map<String, Boolean> open(@AuthenticationPrincipal Actor actor) {
            return Map.of("anonymous", actor == null);
        }

        @PostMapping("/probe/public")
        Map<String, Boolean> openWrite() {
            return Map.of("ok", true);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OpenProbe {

        @Bean
        PublicEndpoints probePublic() {
            return () -> List.of(
                    new PublicEndpoints.Endpoint(HttpMethod.GET, "/probe/public"),
                    new PublicEndpoints.Endpoint(HttpMethod.POST, "/probe/public"));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    TestSessions sessions;

    private final Actor parent = TestSessions.actor(Actor.Role.PARENT);
    private final Actor admin = TestSessions.actor(Actor.Role.ADMIN);

    private MockHttpServletRequestBuilder cookieWrite(String sessionToken) {
        return post("/probe/me").cookie(new Cookie(SecurityConfig.SESSION_COOKIE, sessionToken));
    }

    @Test
    void request_without_session_is_a_401_problem() throws Exception {
        mvc.perform(get("/probe/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.traceId", matchesPattern("^[0-9a-f]{32}$")))
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void unknown_or_revoked_bearer_is_a_401() throws Exception {
        mvc.perform(get("/probe/me").header("Authorization", "Bearer not-a-session"))
                .andExpect(status().isUnauthorized());

        String token = sessions.open(parent);
        mvc.perform(get("/probe/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        sessions.revoke(token);
        mvc.perform(get("/probe/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearer_session_reaches_the_controller_as_the_actor() throws Exception {
        mvc.perform(get("/probe/me").with(sessions.as(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(parent.accountId().toString()))
                .andExpect(jsonPath("$.role").value("PARENT"));
    }

    @Test
    void bearer_write_needs_no_csrf_and_no_origin() throws Exception {
        mvc.perform(post("/probe/me").with(sessions.as(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(parent.accountId().toString()));
    }

    @Test
    void bearer_wins_over_cookie() throws Exception {
        String cookieToken = sessions.open(admin);
        mvc.perform(get("/probe/me").header("Authorization", "Bearer not-a-session")
                        .cookie(new Cookie(SecurityConfig.SESSION_COOKIE, cookieToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cookie_session_reads_without_csrf() throws Exception {
        mvc.perform(get("/probe/me").cookie(new Cookie(SecurityConfig.SESSION_COOKIE, sessions.open(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(header().string("Set-Cookie", containsString(SecurityConfig.CSRF_COOKIE + "=")));
    }

    @Test
    void cookie_write_with_csrf_and_admin_origin_is_accepted() throws Exception {
        mvc.perform(cookieWrite(sessions.open(admin))
                        .cookie(new Cookie(SecurityConfig.CSRF_COOKIE, CSRF))
                        .header(SecurityConfig.CSRF_HEADER, CSRF)
                        .header("Origin", ADMIN_ORIGIN))
                .andExpect(status().isOk());
        mvc.perform(cookieWrite(sessions.open(admin))
                        .cookie(new Cookie(SecurityConfig.CSRF_COOKIE, CSRF))
                        .header(SecurityConfig.CSRF_HEADER, CSRF)
                        .header("Referer", ADMIN_ORIGIN + "/admin/verifications"))
                .andExpect(status().isOk());
    }

    @Test
    void cookie_write_without_csrf_token_is_rejected() throws Exception {
        mvc.perform(cookieWrite(sessions.open(admin)).header("Origin", ADMIN_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        mvc.perform(cookieWrite(sessions.open(admin))
                        .cookie(new Cookie(SecurityConfig.CSRF_COOKIE, CSRF))
                        .header(SecurityConfig.CSRF_HEADER, "another-token-value")
                        .header("Origin", ADMIN_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
    }

    @Test
    void cookie_write_from_another_or_unknown_origin_is_rejected() throws Exception {
        for (var origin : new String[][] {{"Origin", "https://evil.test"}, {"Origin", "null"},
                {"Referer", "https://evil.test/admin"}, {"Referer", "not a url"}}) {
            mvc.perform(cookieWrite(sessions.open(admin))
                            .cookie(new Cookie(SecurityConfig.CSRF_COOKIE, CSRF))
                            .header(SecurityConfig.CSRF_HEADER, CSRF)
                            .header(origin[0], origin[1]))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        }
        mvc.perform(cookieWrite(sessions.open(admin))
                        .cookie(new Cookie(SecurityConfig.CSRF_COOKIE, CSRF))
                        .header(SecurityConfig.CSRF_HEADER, CSRF))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
    }

    @Test
    void public_endpoints_need_no_session() throws Exception {
        mvc.perform(get("/probe/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymous").value(true));
        mvc.perform(post("/probe/public")).andExpect(status().isOk());
    }

    @Test
    void standard_security_headers_are_present() throws Exception {
        mvc.perform(get("/probe/me").with(sessions.as(parent)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }
}
