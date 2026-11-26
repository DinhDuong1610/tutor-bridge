package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The tutor's view of their own verification (OP-23, Issue #96): the server derives four states,
 * shows a reason only for NEEDS_INFO and REJECTED, and never names the verifier. It also names the
 * tutor's current approved version, if any (OP-24 (1), Issue #166).
 */
class TutorVerificationContractTest {

    private static final String PATH = "/api/v1/me/tutor-profile/verification";
    private static final String PROBLEM = """
            {"type":"urn:tutorbridge:problem:ACCESS_UNAVAILABLE","title":"Không tìm thấy.","status":404,
             "code":"ACCESS_UNAVAILABLE","detail":"Không tìm thấy.","traceId":"0123456789abcdef0123456789abcdef"}""";

    private final Contract contract = Contract.load(Contract.FILE);

    /** A view of version 2; a verified one is the approved version, otherwise there is none. */
    private static String view(String state, String reason, String decidedAt) {
        return view(state, reason, decidedAt, "VERIFIED".equals(state) ? "2" : "null");
    }

    private static String view(String state, String reason, String decidedAt, String approvedVersion) {
        return """
                {"id":"5f0c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10","profileVersionId":"7a1d3c0f-3b62-4b9f-8b54-7e2a1d3c0f21",
                 "profileVersion":2,"state":"%s","scope":"Toán đại số lớp 8","reason":%s,"decidedAt":%s,
                 "approvedVersion":%s}"""
                .formatted(state, reason == null ? "null" : "\"" + reason + "\"",
                        decidedAt == null ? "null" : "\"" + decidedAt + "\"", approvedVersion);
    }

    private List<String> check(int status, String contentType, String body) throws IOException {
        var response = new MockHttpServletResponse();
        response.setStatus(status);
        response.setContentType(contentType);
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        return contract.violations("GET", PATH, response);
    }

    private List<String> check(String body) throws IOException {
        return check(200, "application/json", body);
    }

    @Test
    void each_state_with_its_fields_follows_the_contract() throws IOException {
        assertThat(check(view("WAITING", null, null))).isEmpty();
        assertThat(check(view("NEEDS_INFO", "Bổ sung bằng tốt nghiệp.", "2026-11-12T03:00:00Z"))).isEmpty();
        assertThat(check(view("VERIFIED", null, "2026-11-12T03:00:00Z"))).isEmpty();
        assertThat(check(view("REJECTED", "Minh chứng không khớp phạm vi.", "2026-11-12T03:00:00Z"))).isEmpty();
    }

    @Test
    void a_tutor_who_never_submitted_gets_a_problem() throws IOException {
        assertThat(check(404, "application/problem+json", PROBLEM)).isEmpty();
    }

    @Test
    void the_reason_is_only_shown_when_more_is_needed_or_the_profile_is_rejected() throws IOException {
        assertThat(check(view("VERIFIED", "Ghi chú nội bộ của người duyệt.", "2026-11-12T03:00:00Z"))).isNotEmpty();
        assertThat(check(view("WAITING", "Ghi chú nội bộ của người duyệt.", null))).isNotEmpty();
        assertThat(check(view("NEEDS_INFO", null, "2026-11-12T03:00:00Z"))).isNotEmpty();
        assertThat(check(view("REJECTED", null, "2026-11-12T03:00:00Z"))).isNotEmpty();
    }

    @Test
    void only_a_decided_verification_has_a_decision_time() throws IOException {
        assertThat(check(view("WAITING", null, "2026-11-12T03:00:00Z"))).isNotEmpty();
        assertThat(check(view("VERIFIED", null, null))).isNotEmpty();
        assertThat(check(view("NEEDS_INFO", "Bổ sung bằng tốt nghiệp.", null))).isNotEmpty();
    }

    @Test
    void an_edit_waiting_or_refused_still_names_the_approved_version_parents_see() throws IOException {
        // #166: version 2 waits, needs more or is refused while version 1 stays public (OP-24 (1)).
        assertThat(check(view("WAITING", null, null, "1"))).isEmpty();
        assertThat(check(view("NEEDS_INFO", "Bổ sung bằng tốt nghiệp.", "2026-11-12T03:00:00Z", "1"))).isEmpty();
        assertThat(check(view("REJECTED", "Minh chứng không khớp phạm vi.", "2026-11-12T03:00:00Z", "1"))).isEmpty();
    }

    @Test
    void the_approved_version_is_always_present_and_a_verified_view_has_one() throws IOException {
        String missing = view("WAITING", null, null).replaceAll(",\\s*\"approvedVersion\":null", "");
        assertThat(missing).doesNotContain("approvedVersion");
        assertThat(check(missing)).isNotEmpty();
        assertThat(check(view("VERIFIED", null, "2026-11-12T03:00:00Z", "null"))).isNotEmpty();
        assertThat(check(view("WAITING", null, null, "0"))).isNotEmpty();
        assertThat(check(view("WAITING", null, null, "\"1\""))).isNotEmpty();
    }

    @Test
    void the_old_pending_status_is_not_a_state_of_this_view() throws IOException {
        assertThat(check(view("PENDING", null, null))).isNotEmpty();
    }

    @Test
    void the_verifier_and_the_evidence_never_appear() throws IOException {
        String waiting = view("WAITING", null, null);
        assertThat(check(waiting.replace("}", ",\"assignedTo\":\"9e10c2b9-2a51-4a8e-9a43-6d1f0c2b9e10\"}")))
                .singleElement().asString().contains("assignedTo");
        assertThat(check(waiting.replace("}", ",\"evidenceKey\":\"tutor/evidence/1.pdf\"}")))
                .singleElement().asString().contains("evidenceKey");
    }
}
