package com.FaceLit.backend.auth.exception;

// Se lanza cuando algo falla en el login.
public class LoginException extends RuntimeException {
    public LoginException(String message) {
        super(message);

}

}