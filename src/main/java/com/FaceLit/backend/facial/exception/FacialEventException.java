package com.FaceLit.backend.facial.exception;

import org.springframework.http.HttpStatus;

public class FacialEventException extends RuntimeException {

    private final HttpStatus status;

    public FacialEventException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public FacialEventException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
