package com.tutorbridge.platform.web;

import com.tutorbridge.platform.api.ApiException;
import com.tutorbridge.platform.api.ApiException.FieldProblem;
import com.tutorbridge.platform.api.TraceContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** The one place where failures become application/problem+json. */
@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> business(ApiException e) {
        return problem(new HttpHeaders(), e.status(), e.code(), e.title(), e.fields());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception e) {
        LOG.error("Unhandled request failure", e);
        return problem(new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Hệ thống đang gặp lỗi. Vui lòng thử lại sau.", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> fields = e.getBindingResult().getFieldErrors().stream()
                .limit(50)
                .map(error -> new FieldProblem(error.getField(), error.getDefaultMessage()))
                .toList();
        return problem(headers, HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED",
                "Dữ liệu chưa hợp lệ.", fields);
    }

    /** Constraints on query, path and header parameters, such as {@code @Min} on {@code limit}. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> fields = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldProblem(parameterName(result.getMethodParameter()),
                                error.getDefaultMessage())))
                .limit(50)
                .toList();
        return problem(headers, HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED",
                "Dữ liệu chưa hợp lệ.", fields);
    }

    private static String parameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        return name == null ? "arg" + parameter.getParameterIndex() : name;
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(headers, HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED",
                "Dữ liệu chưa hợp lệ.", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(headers, status, "REQUEST_REJECTED", "Yêu cầu không được chấp nhận.", List.of());
    }

    private ResponseEntity<Object> problem(
            HttpHeaders headers, HttpStatusCode status, String code, String title, List<FieldProblem> fields) {
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Problem.of(status.value(), code, title, TraceContext.current(), fields));
    }
}
