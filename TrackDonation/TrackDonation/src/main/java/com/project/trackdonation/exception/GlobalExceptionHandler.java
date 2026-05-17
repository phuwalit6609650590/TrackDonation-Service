package com.project.trackdonation.exception;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.project.trackdonation.configuration.TraceIdFilter.TRACE_ID_HEADER;
import static com.project.trackdonation.configuration.TraceIdFilter.TRACE_ID_MDC_KEY;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String getTraceId() {
        return MDC.get(TRACE_ID_MDC_KEY) != null ? MDC.get(TRACE_ID_MDC_KEY) : "UNKNOWN";
    }

    private HttpHeaders createHeadersWithTraceId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(TRACE_ID_HEADER, getTraceId());
        return headers;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
                        (existing, replacement) -> existing));
        log.warn("Validation failed: {}", fieldErrors);
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now());
        errorBody.put("status", HttpStatus.BAD_REQUEST.value());
        errorBody.put("errorCode", "ERR_VALIDATION");
        errorBody.put("message", "Request validation failed");
        errorBody.put("fieldErrors", fieldErrors);
        errorBody.put("traceId", getTraceId());
        return new ResponseEntity<>(errorBody, createHeadersWithTraceId(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleIncidentNotFound(IncidentNotFoundException ex) {
        log.warn("IncidentNotFoundException: {}", ex.getMessage());
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now());
        errorBody.put("status", HttpStatus.NOT_FOUND.value());
        errorBody.put("errorCode", "ERR_INCIDENT_NOT_FOUND");
        errorBody.put("message", ex.getMessage());
        errorBody.put("suggestion", "Please verify the incident ID is correct and active.");
        errorBody.put("traceId", getTraceId());

        return new ResponseEntity<>(errorBody, createHeadersWithTraceId(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(IncidentServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleIncidentServiceUnavailable(
            IncidentServiceUnavailableException ex) {
        log.error("IncidentServiceUnavailableException: {}", ex.getMessage());
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now());
        errorBody.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        errorBody.put("errorCode", "ERR_INCIDENT_SERVICE_UNAVAILABLE");
        errorBody.put("message", ex.getMessage());
        errorBody.put("suggestion", "Please try again later.");
        errorBody.put("traceId", getTraceId());

        return new ResponseEntity<>(errorBody, createHeadersWithTraceId(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now());
        errorBody.put("status", HttpStatus.BAD_REQUEST.value());
        errorBody.put("errorCode", "ERR_BAD_REQUEST");
        errorBody.put("message", ex.getMessage());
        errorBody.put("traceId", getTraceId());

        return new ResponseEntity<>(errorBody, createHeadersWithTraceId(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now());
        errorBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorBody.put("errorCode", "ERR_INTERNAL_SERVER");
        errorBody.put("message", "An unexpected error occurred. Please contact support.");
        errorBody.put("suggestion", "Please try again later.");
        errorBody.put("traceId", getTraceId());

        return new ResponseEntity<>(errorBody, createHeadersWithTraceId(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
