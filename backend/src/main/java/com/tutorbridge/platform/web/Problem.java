package com.tutorbridge.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import java.util.List;

/** The Problem schema in contracts/openapi.yaml, which allows no other properties. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record Problem(String type, String title, int status, String code, String traceId, List<FieldProblem> fields) {

    static Problem of(int status, String code, String title, String traceId, List<FieldProblem> fields) {
        return new Problem("urn:tutorbridge:problem:" + code, title, status, code, traceId, fields);
    }
}
