package com.tutorbridge.platform.api;

import java.util.List;
import org.springframework.http.HttpStatus;

/** A business failure. The title is shown to users, so it is Vietnamese and holds no private data. */
public class ApiException extends RuntimeException {

    public record FieldProblem(String path, String message) {}

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final List<FieldProblem> fields;

    public ApiException(HttpStatus status, String code, String title) {
        this(status, code, title, List.of());
    }

    public ApiException(HttpStatus status, String code, String title, List<FieldProblem> fields) {
        super(code);
        this.status = status;
        this.code = code;
        this.title = title;
        this.fields = List.copyOf(fields);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public List<FieldProblem> fields() {
        return fields;
    }
}
