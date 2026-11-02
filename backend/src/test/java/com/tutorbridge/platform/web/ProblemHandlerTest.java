package com.tutorbridge.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.TraceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ProblemHandlerTest.Probe.class)
@Import(ProblemHandlerTest.Probe.class)
class ProblemHandlerTest {

    private static final String TRACE_ID = "^[0-9a-f]{32}$";

    @RestController
    static class Probe {

        record Body(@NotBlank(message = "Không được để trống.") String name) {}

        @GetMapping("/probe/business")
        void business() {
            throw new ApiException(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT", "Lịch vừa bị trùng.");
        }

        @GetMapping("/probe/crash")
        void crash() {
            throw new IllegalStateException("SECRET internal detail");
        }

        @PostMapping("/probe/body")
        void body(@Valid @RequestBody Body body) {}

        @GetMapping("/probe/ok")
        void ok() {}
    }

    @Autowired
    MockMvc mvc;

    @Test
    void business_failure_keeps_its_status_and_stable_code() throws Exception {
        mvc.perform(get("/probe/business"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Lịch vừa bị trùng."))
                .andExpect(jsonPath("$.type").value("urn:tutorbridge:problem:SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.traceId", matchesPattern(TRACE_ID)))
                .andExpect(jsonPath("$", not(hasKey("fields"))))
                .andExpect(jsonPath("$", not(hasKey("instance"))));
    }

    @Test
    void unexpected_failure_hides_internal_detail() throws Exception {
        mvc.perform(get("/probe/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId", matchesPattern(TRACE_ID)))
                .andExpect(content().string(not(containsString("SECRET"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void invalid_body_is_a_422_with_field_messages() throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0].path").value("name"))
                .andExpect(jsonPath("$.fields[0].message").value("Không được để trống."));
    }

    @Test
    void unreadable_body_is_a_422_without_parser_detail() throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(content().string(not(containsString("Unexpected"))));
    }

    @Test
    void framework_rejections_use_the_problem_shape() throws Exception {
        mvc.perform(post("/probe/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"))
                .andExpect(jsonPath("$.traceId", matchesPattern(TRACE_ID)));
        mvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void trace_id_does_not_leak_past_the_request_and_ignores_client_headers() throws Exception {
        mvc.perform(get("/probe/business").header("X-Trace-Id", "evil\nvalue"))
                .andExpect(jsonPath("$.traceId", matchesPattern(TRACE_ID)));

        assertThat(TraceContext.current()).isNull();
    }
}
