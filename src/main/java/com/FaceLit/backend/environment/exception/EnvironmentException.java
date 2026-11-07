package com.FaceLit.backend.environment.exception;

import org.springframework.http.HttpStatus;

public class EnvironmentException extends RuntimeException {

    private final HttpStatus status;

    public EnvironmentException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public EnvironmentException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
