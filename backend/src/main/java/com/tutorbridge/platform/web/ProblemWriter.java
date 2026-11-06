package com.tutorbridge.platform.web;

import com.tutorbridge.platform.api.TraceContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes a Problem from a filter, where {@link ProblemHandler} does not reach. Same shape. */
@Component
class ProblemWriter {

    private final JsonMapper json;

    ProblemWriter(JsonMapper json) {
        this.json = json;
    }

    void write(HttpServletResponse response, int status, String code, String title) throws IOException {
        response.setStatus(status);
        // Same content type as ProblemHandler. JSON bytes are UTF-8 by definition (RFC 8259).
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), Problem.of(status, code, title, TraceContext.current(), List.of()));
    }
}
