package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * What an assigned verifier reads for AD03 and AD04 (OP-28, Issue #98, Đính's answers of 21/11/2026):
 * the queue names the tutor and the submission time, a PENDING record with a reason is NEEDS_INFO,
 * a replaced version is flagged, and the detail gives the submitted version with its evidence list but
 * never the evidence bytes, key or hash.
 */
class AssignedVerificationContractTest {

    private static final String ID = "5f0c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10";
    private static final String LIST = "/api/v1/admin/verifications";
    private static final String DETAIL = LIST + "/" + ID;
    private static final String SUBMITTED = "\"2026-11-20T03:00:00Z\"";
    private static final String DECIDED = "\"2026-11-21T03:00:00Z\"";

    private final Contract contract = Contract.load(Contract.FILE);

    private static String record(String status, String reason, String decidedAt, boolean superseded) {
        return """
                {"id":"%s","profileVersionId":"7a1d3c0f-3b62-4b9f-8b54-7e2a1d3c0f21","profileVersion":2,
                 "tutorDisplayName":"Gia sư thử","assignedTo":"9e10c2b9-2a51-4a8e-9a43-6d1f0c2b9e10",
                 "status":"%s","scope":"Toán đại số lớp 8","reason":%s,"submittedAt":%s,"decidedAt":%s,
                 "superseded":%s,"revision":1}"""
                .formatted(ID, status, reason == null ? "null" : "\"" + reason + "\"", SUBMITTED,
                        decidedAt == null ? "null" : decidedAt, superseded);
    }

    private static String waiting() {
        return record("PENDING", null, null, false);
    }

    private static String page(String... items) {
        return "{\"items\":[" + String.join(",", items) + "],\"nextCursor\":null}";
    }

    private static String detail(String verification, String evidence) {
        return """
                {"verification":%s,
                 "profile":{"id":"7a1d3c0f-3b62-4b9f-8b54-7e2a1d3c0f21","version":2,"subject":"MATH8_ALGEBRA",
                  "description":"Dạy Toán 8 phần đại số, tập trung phân thức và phương trình bậc nhất.",
                  "experienceMonths":0,"rateVnd60":180000},
                 "evidence":[%s]}""".formatted(verification, evidence);
    }

    private static final String PDF = """
            {"id":"0b3c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10","mediaType":"application/pdf","sizeBytes":5242880}""";

    private List<String> check(String path, String body) throws IOException {
        var response = new MockHttpServletResponse();
        response.setStatus(200);
        response.setContentType("application/json");
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        return contract.violations("GET", path, response);
    }

    @Test
    void the_queue_shows_each_stored_status_with_tutor_name_and_submission_time() throws IOException {
        assertThat(check(LIST, page(
                waiting(),
                record("PENDING", "Bổ sung bằng tốt nghiệp.", DECIDED, false),
                record("VERIFIED", "Minh chứng đủ cho phạm vi.", DECIDED, false),
                record("REJECTED", "Minh chứng không khớp phạm vi.", DECIDED, false),
                record("PENDING", "Bổ sung bằng tốt nghiệp.", DECIDED, true)))).isEmpty();
        assertThat(check(LIST, page())).isEmpty();
    }

    @Test
    void a_tutor_without_a_display_name_is_null_not_missing() throws IOException {
        assertThat(check(LIST, page(waiting().replace("\"Gia sư thử\"", "null")))).isEmpty();
        String noName = waiting().replace("\"tutorDisplayName\":\"Gia sư thử\",", "");
        assertThat(noName).doesNotContain("tutorDisplayName");
        assertThat(check(LIST, page(noName))).isNotEmpty();
    }

    @Test
    void every_record_has_a_submission_time_and_the_superseded_flag() throws IOException {
        String noTime = waiting().replace("\"submittedAt\":" + SUBMITTED, "\"submittedAt\":null");
        assertThat(noTime).contains("\"submittedAt\":null");
        assertThat(check(LIST, page(noTime))).isNotEmpty();
        String noFlag = waiting().replaceAll(",\\s*\"superseded\":false", "");
        assertThat(noFlag).doesNotContain("superseded");
        assertThat(check(LIST, page(noFlag))).isNotEmpty();
    }

    @Test
    void a_decided_record_has_a_reason_and_a_decision_time() throws IOException {
        assertThat(check(LIST, page(record("VERIFIED", null, DECIDED, false)))).isNotEmpty();
        assertThat(check(LIST, page(record("REJECTED", "Minh chứng không khớp phạm vi.", null, false)))).isNotEmpty();
    }

    @Test
    void a_pending_record_has_a_decision_time_only_when_more_was_asked() throws IOException {
        assertThat(check(LIST, page(record("PENDING", null, DECIDED, false)))).isNotEmpty();
        assertThat(check(LIST, page(record("PENDING", "Bổ sung bằng tốt nghiệp.", null, false)))).isNotEmpty();
    }

    @Test
    void the_derived_states_of_the_tutor_view_are_not_stored_statuses() throws IOException {
        assertThat(check(LIST, page(record("NEEDS_INFO", "Bổ sung bằng tốt nghiệp.", DECIDED, false)))).isNotEmpty();
        assertThat(check(LIST, page(record("WAITING", null, null, false)))).isNotEmpty();
    }

    @Test
    void no_email_or_evidence_key_reaches_the_queue() throws IOException {
        assertThat(check(LIST, page(waiting().replace("}", ",\"tutorEmail\":\"gia.su@example.invalid\"}"))))
                .isNotEmpty();
        assertThat(check(LIST, page(waiting().replace("}", ",\"evidenceKey\":\"tutor/evidence/1.pdf\"}"))))
                .isNotEmpty();
    }

    @Test
    void the_detail_gives_the_submitted_version_and_its_evidence_list() throws IOException {
        assertThat(check(DETAIL, detail(waiting(), PDF))).isEmpty();
        assertThat(check(DETAIL, detail(waiting(), ""))).isEmpty();
        String image = PDF.replace("application/pdf", "image/png").replace("5242880", "1");
        assertThat(check(DETAIL, detail(waiting(), PDF + "," + image))).isEmpty();
    }

    @Test
    void an_evidence_item_is_metadata_only() throws IOException {
        assertThat(check(DETAIL, detail(waiting(), PDF.replace("}", ",\"sha256\":\"" + "0".repeat(64) + "\"}"))))
                .isNotEmpty();
        assertThat(check(DETAIL, detail(waiting(), PDF.replace("}", ",\"objectKey\":\"tutor/evidence/1.pdf\"}"))))
                .isNotEmpty();
        assertThat(check(DETAIL, detail(waiting(), PDF.replace("application/pdf", "image/gif")))).isNotEmpty();
    }

    @Test
    void the_detail_holds_at_most_the_ten_files_a_submission_may_have() throws IOException {
        String eleven = String.join(",", java.util.Collections.nCopies(11, PDF));
        assertThat(check(DETAIL, detail(waiting(), eleven))).isNotEmpty();
        String ten = String.join(",", java.util.Collections.nCopies(10, PDF));
        assertThat(check(DETAIL, detail(waiting(), ten))).isEmpty();
    }
}
