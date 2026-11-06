package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class ContractTest {

    private static final String STUDENT_ID = "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10";
    private static final String STUDENT =
            "{\"id\":\"" + STUDENT_ID + "\",\"displayName\":\"Minh\",\"grade\":8,\"revision\":1}";
    private static final String PROBLEM = """
            {"type":"urn:tutorbridge:problem:ACCESS_UNAVAILABLE","title":"Không tìm thấy.","status":404,
             "code":"ACCESS_UNAVAILABLE","detail":"Không tìm thấy.","traceId":"0123456789abcdef0123456789abcdef"}""";

    private final Contract contract = Contract.load(Contract.FILE);

    private static MockHttpServletResponse response(int status, String contentType, String body) throws IOException {
        var response = new MockHttpServletResponse();
        response.setStatus(status);
        if (contentType != null) {
            response.setContentType(contentType);
        }
        if (body != null) {
            response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        return response;
    }

    private List<String> check(String method, String path, int status, String contentType, String body)
            throws IOException {
        return contract.violations(method, path, response(status, contentType, body));
    }

    @Test
    void a_response_that_follows_the_contract_passes() throws IOException {
        assertThat(check("GET", "/api/v1/students", 200, "application/json",
                "{\"items\":[" + STUDENT + "],\"nextCursor\":null}")).isEmpty();
        assertThat(check("GET", "/api/v1/students/" + STUDENT_ID, 404, "application/problem+json", PROBLEM))
                .isEmpty();
    }

    @Test
    void a_property_outside_the_schema_is_a_violation() throws IOException {
        String leaky = STUDENT.replace("}", ",\"parentEmail\":\"a@b.test\"}");
        assertThat(check("GET", "/api/v1/students", 200, "application/json",
                "{\"items\":[" + leaky + "],\"nextCursor\":null}"))
                .singleElement().asString().contains("parentEmail");
    }

    @Test
    void a_missing_required_property_is_a_violation() throws IOException {
        String noRevision = STUDENT.replace(",\"revision\":1", "");
        assertThat(check("GET", "/api/v1/students", 200, "application/json",
                "{\"items\":[" + noRevision + "],\"nextCursor\":null}"))
                .singleElement().asString().contains("revision");
        assertThat(check("GET", "/api/v1/students/" + STUDENT_ID, 404, "application/problem+json",
                PROBLEM.replace(",\"traceId\":\"0123456789abcdef0123456789abcdef\"", "")))
                .singleElement().asString().contains("traceId");
    }

    @Test
    void a_wrong_value_is_a_violation() throws IOException {
        assertThat(check("GET", "/api/v1/students", 200, "application/json",
                "{\"items\":[" + STUDENT.replace("\"grade\":8", "\"grade\":9") + "],\"nextCursor\":null}"))
                .isNotEmpty();
    }

    @Test
    void an_undeclared_status_or_content_type_is_a_violation() throws IOException {
        assertThat(check("GET", "/api/v1/students", 418, null, null))
                .singleElement().asString().contains("does not declare status 418");
        assertThat(check("GET", "/api/v1/students", 200, "text/plain", "hello"))
                .singleElement().asString().contains("does not declare content type");
    }

    @Test
    void an_operation_outside_the_contract_is_a_violation() throws IOException {
        assertThat(check("GET", "/api/v1/students/" + STUDENT_ID + "/secrets", 200, "application/json", "{}"))
                .singleElement().asString().contains("No operation");
        assertThat(check("DELETE", "/api/v1/students", 204, null, null))
                .singleElement().asString().contains("No operation");
        assertThat(check("GET", "/internal/v1/jobs", 200, "application/json", "{}"))
                .singleElement().asString().contains("outside /api/v1");
    }
}
