package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The tutor uploads evidence bytes to the backend (Issue #159, from #12 Q1): one PUT per upload,
 * only PDF, JPEG or PNG, with an Idempotency-Key (INV-20), answering with the upload record and
 * never a public URL.
 */
class EvidenceContentContractTest {

    private static final String PATH =
            "/api/v1/me/tutor-profile/evidence-uploads/5f0c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10/content";
    private static final String UPLOADING = """
            {"id":"5f0c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10","state":"UPLOADING","expiresAt":"2026-11-17T03:15:00Z"}""";

    private final Contract contract = Contract.load(Contract.FILE);

    private static String problem(int status, String code) {
        return """
                {"type":"urn:tutorbridge:problem:%s","title":"Lỗi.","status":%d,"code":"%s","detail":"Lỗi.",
                 "traceId":"0123456789abcdef0123456789abcdef"}""".formatted(code, status, code);
    }

    private List<String> check(int status, String contentType, String body) throws IOException {
        var response = new MockHttpServletResponse();
        response.setStatus(status);
        response.setContentType(contentType);
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        return contract.violations("PUT", PATH, response);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> operation() throws IOException {
        var yaml = (Map<String, Object>) new Load(LoadSettings.builder().setMaxAliasesForCollections(1000).build())
                .loadFromString(Files.readString(Contract.FILE, StandardCharsets.UTF_8));
        var paths = (Map<String, Object>) yaml.get("paths");
        var path = (Map<String, Object>) paths.get("/me/tutor-profile/evidence-uploads/{uploadId}/content");
        return (Map<String, Object>) path.get("put");
    }

    @Test
    void stored_bytes_answer_with_the_upload_still_uploading() throws IOException {
        assertThat(check(200, "application/json", UPLOADING)).isEmpty();
    }

    @Test
    void the_answer_never_carries_a_url_or_the_object_key() throws IOException {
        assertThat(check(200, "application/json",
                UPLOADING.replace("}", ",\"url\":\"https://files.invalid/evidence/1\"}"))).isNotEmpty();
        assertThat(check(200, "application/json",
                UPLOADING.replace("}", ",\"objectKey\":\"evidence/1\"}"))).isNotEmpty();
    }

    @Test
    void an_expired_or_closed_upload_is_a_conflict() throws IOException {
        assertThat(check(409, "application/problem+json", problem(409, "EVIDENCE_UPLOAD_EXPIRED"))).isEmpty();
        assertThat(check(409, "application/problem+json", problem(409, "EVIDENCE_UPLOAD_CLOSED"))).isEmpty();
        assertThat(check(422, "application/problem+json", problem(422, "VALIDATION_FAILED"))).isEmpty();
        assertThat(check(404, "application/problem+json", problem(404, "ACCESS_UNAVAILABLE"))).isEmpty();
    }

    @Test
    void there_is_no_version_precondition() throws IOException {
        assertThat(check(412, "application/problem+json", problem(412, "VERSION_CONFLICT"))).isNotEmpty();
        assertThat(check(428, "application/problem+json", problem(428, "PRECONDITION_REQUIRED"))).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void the_upload_needs_an_idempotency_key() throws IOException {
        var parameters = (List<Map<String, Object>>) operation().get("parameters");
        assertThat(parameters).extracting(p -> p.get("$ref"))
                .contains("#/components/parameters/IdempotencyKey");
    }

    @Test
    void the_receipt_fingerprint_names_the_upload_and_the_bytes() throws IOException {
        // Review of #163: the same file sent to two uploads with one key must not replay the first upload.
        String description = ((String) operation().get("description")).replaceAll("\s+", " ");
        assertThat(description).contains("the receipt payload is {uploadId, bodySha256}")
                .contains("same key with another uploadId or other bytes is 409");
    }

    @Test
    @SuppressWarnings("unchecked")
    void only_pdf_jpeg_and_png_bytes_are_accepted() throws IOException {
        var body = (Map<String, Object>) operation().get("requestBody");
        assertThat(body.get("required")).isEqualTo(true);
        assertThat(((Map<String, Object>) body.get("content")).keySet())
                .containsExactlyInAnyOrder("application/pdf", "image/jpeg", "image/png");
    }
}
