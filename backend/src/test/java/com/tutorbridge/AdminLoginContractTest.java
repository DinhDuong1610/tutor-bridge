package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The admin sign-in apart from loginAccount (TB-8 Q17, Issue #136): loginAdmin only issues an MFA
 * challenge, loginAccount only ever answers with a session, and getAdminSession describes an ADMIN
 * session that completed MFA without carrying its token.
 */
class AdminLoginContractTest {

    private static final String ADMIN_LOGIN = "/api/v1/auth/admin-login";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ADMIN_SESSION = "/api/v1/admin/session";

    private static final String CHALLENGE = """
            {"mfaRequired":true,"challengeToken":"challenge-token-not-a-secret-0001","expiresAt":"2026-11-13T03:05:00Z"}""";
    private static final String CREDENTIAL = """
            {"sessionId":"5f0c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10","accessToken":"test-access-token-not-a-secret",
             "role":"PARENT","idleExpiresAt":"2026-11-14T03:00:00Z","absoluteExpiresAt":"2026-11-20T03:00:00Z"}""";
    private static final String ADMIN_VIEW = """
            {"sessionId":"5f0c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10","role":"ADMIN","mfaVerifiedAt":"2026-11-13T03:00:00Z",
             "idleExpiresAt":"2026-11-13T03:30:00Z","absoluteExpiresAt":"2026-11-13T15:00:00Z"}""";

    private final Contract contract = Contract.load(Contract.FILE);

    private static String problem(int status, String code) {
        return """
                {"type":"urn:tutorbridge:problem:%s","title":"Lỗi.","status":%d,"code":"%s","detail":"Lỗi.",
                 "traceId":"0123456789abcdef0123456789abcdef"}""".formatted(code, status, code);
    }

    private List<String> check(String method, String path, int status, String contentType, String body)
            throws IOException {
        var response = new MockHttpServletResponse();
        response.setStatus(status);
        response.setContentType(contentType);
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        return contract.violations(method, path, response);
    }

    private List<String> ok(String method, String path, String body) throws IOException {
        return check(method, path, 200, "application/json", body);
    }

    @Test
    void admin_login_answers_with_a_challenge_and_never_a_session() throws IOException {
        assertThat(ok("POST", ADMIN_LOGIN, CHALLENGE)).isEmpty();
        assertThat(ok("POST", ADMIN_LOGIN, CREDENTIAL)).isNotEmpty();
    }

    @Test
    void admin_login_failures_are_problems() throws IOException {
        assertThat(check("POST", ADMIN_LOGIN, 401, "application/problem+json",
                problem(401, "AUTHENTICATION_FAILED"))).isEmpty();
        assertThat(check("POST", ADMIN_LOGIN, 403, "application/problem+json",
                problem(403, "ACCOUNT_LOCKED"))).isEmpty();
        assertThat(check("POST", ADMIN_LOGIN, 429, "application/problem+json",
                problem(429, "LOGIN_DELAYED"))).isEmpty();
    }

    @Test
    void account_login_only_answers_with_a_session() throws IOException {
        assertThat(ok("POST", LOGIN, CREDENTIAL)).isEmpty();
        assertThat(ok("POST", LOGIN, CHALLENGE)).isNotEmpty();
    }

    @Test
    void the_admin_session_is_an_admin_that_completed_mfa() throws IOException {
        assertThat(ok("GET", ADMIN_SESSION, ADMIN_VIEW)).isEmpty();
        assertThat(ok("GET", ADMIN_SESSION, ADMIN_VIEW.replace("\"ADMIN\"", "\"PARENT\""))).isNotEmpty();
        assertThat(ok("GET", ADMIN_SESSION, ADMIN_VIEW.replace("\"mfaVerifiedAt\":\"2026-11-13T03:00:00Z\",", "")))
                .singleElement().asString().contains("mfaVerifiedAt");
    }

    @Test
    void the_admin_session_never_carries_its_token() throws IOException {
        assertThat(ok("GET", ADMIN_SESSION, ADMIN_VIEW.replace("}", ",\"accessToken\":\"test-access-token-not-a-secret\"}")))
                .singleElement().asString().contains("accessToken");
    }

    @Test
    void no_session_and_not_an_admin_are_problems() throws IOException {
        assertThat(check("GET", ADMIN_SESSION, 401, "application/problem+json",
                problem(401, "AUTHENTICATION_REQUIRED"))).isEmpty();
        assertThat(check("GET", ADMIN_SESSION, 403, "application/problem+json",
                problem(403, "ACCESS_DENIED"))).isEmpty();
    }
}
