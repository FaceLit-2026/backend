package com.FaceLit.backend.academic.exception;

import org.springframework.http.HttpStatus;

public class AcademicException extends RuntimeException {

    private final HttpStatus status;

    public AcademicException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public AcademicException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}