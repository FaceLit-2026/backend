package com.FaceLit.backend.auth.exception;

import org.springframework.http.HttpStatus;

public class UserConfigurationException extends RuntimeException {

    private final HttpStatus status;

    public UserConfigurationException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public UserConfigurationException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

}
