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
 * The need contract after Dinh's answers on #16 (17/11/2026): the schedule is the child's calendar,
 * so a need names the calendar version it was confirmed with and carries no windows of its own.
 */
class NeedContractTest {

    private static final String NEED_ID = "5f0c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10";
    private static final String DRAFT = """
            {"goalVersion":1}""";

    private final Contract contract = Contract.load(Contract.FILE);

    private static String need(String status, String calendarId, String draft) {
        return """
                {"id":"%s","studentId":"7a1d3c0f-3b62-4b9f-8b54-7e2a1d3c0f21","revision":3,
                 "versionId":"9e10c2b9-2a51-4a8e-9a43-6d1f0c2b9e10","status":"%s","draft":%s,"studentCalendarId":%s}"""
                .formatted(NEED_ID, status, draft, calendarId == null ? "null" : "\"" + calendarId + "\"");
    }

    private static final String GOAL_ONLY = """
            {"goal":"Củng cố phân thức đại số trước kỳ kiểm tra giữa kỳ.","budgetVndSession":300000,
             "durationMinutes":90,"frequencyPer7Days":2,"cycleStartDate":"2026-11-20"}""";

    private List<String> check(String method, String path, String body) throws IOException {
        var response = new MockHttpServletResponse();
        response.setStatus(200);
        response.setContentType("application/json");
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        return contract.violations(method, path, response);
    }

    private List<String> read(String body) throws IOException {
        return check("GET", "/api/v1/needs/" + NEED_ID, body);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String name) throws IOException {
        var yaml = (Map<String, Object>) new Load(LoadSettings.builder().setMaxAliasesForCollections(1000).build())
                .loadFromString(Files.readString(Contract.FILE, StandardCharsets.UTF_8));
        var schemas = (Map<String, Object>) ((Map<String, Object>) yaml.get("components")).get("schemas");
        return (Map<String, Object>) schemas.get(name);
    }

    @Test
    void a_confirmed_need_names_its_calendar_version_and_a_draft_has_none_yet() throws IOException {
        assertThat(read(need("CONFIRMED", "0b3c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10", GOAL_ONLY))).isEmpty();
        assertThat(read(need("DRAFT", null, GOAL_ONLY))).isEmpty();
        assertThat(read(need("DRAFT", null, "{}"))).isEmpty();
    }

    @Test
    void every_answer_states_the_calendar_version_even_when_empty() throws IOException {
        String withoutCalendar = need("DRAFT", null, GOAL_ONLY).replace(",\"studentCalendarId\":null", "");
        assertThat(read(withoutCalendar)).singleElement().asString().contains("studentCalendarId");
    }

    @Test
    void the_draft_does_not_take_unknown_fields() throws IOException {
        assertThat(read(need("DRAFT", null, DRAFT))).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void the_need_windows_are_deprecated_in_favour_of_the_child_calendar() throws IOException {
        var properties = (Map<String, Object>) schema("NeedDraftInput").get("properties");
        for (String field : List.of("availableWindows", "preferredWindows")) {
            var property = (Map<String, Object>) properties.get(field);
            assertThat(property.get("deprecated")).as(field).isEqualTo(true);
            assertThat((String) property.get("description")).as(field).contains("getStudentCalendar");
        }
    }
}
