package com.project.trackdonation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class IncidentServiceUnavailableException extends RuntimeException {
    public IncidentServiceUnavailableException(String message) {
        super(message);
    }
}