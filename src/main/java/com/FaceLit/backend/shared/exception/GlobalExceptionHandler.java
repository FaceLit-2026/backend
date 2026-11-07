package com.FaceLit.backend.shared.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Imports nuevos en GlobalExceptionHandler.java
import com.FaceLit.backend.auth.exception.LoginException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.FaceLit.backend.auth.exception.PasswordRecoveryException;
import com.FaceLit.backend.auth.exception.UserManagementException;
import com.FaceLit.backend.auth.exception.UserConfigurationException;
import com.FaceLit.backend.auth.exception.ChangePasswordException;
import com.FaceLit.backend.academic.exception.AcademicException;
import com.FaceLit.backend.environment.exception.EnvironmentException;
import com.FaceLit.backend.facial.exception.FacialEventException;

@RestControllerAdvice // esta anotacion funciona para que se escuchen TODOS los errores que ocurre en
// los controller
public class GlobalExceptionHandler { // esta clase es para que capture errores feos y devuelve un JSON bonito

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", ex.getReason() == null ? "Solicitud rechazada" : ex.getReason()));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<Map<String, String>> handleInvalidParameter(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Revisa los parámetros de la solicitud."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidacion(
            MethodArgumentNotValidException ex) {

        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(errores);
    }

    @ExceptionHandler(com.FaceLit.backend.auth.exception.RecoverySecurityException.class)
    public ResponseEntity<Map<String, String>> handleRecoverySecurity(com.FaceLit.backend.auth.exception.RecoverySecurityException ex) {
        var response = ResponseEntity.status(ex.getStatus());
        if (ex.getRetryAfter() > 0) response.header("Retry-After", Long.toString(ex.getRetryAfter()));
        return response.body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(PasswordRecoveryException.class)
    public ResponseEntity<Map<String, String>> handlePasswordRecovery(PasswordRecoveryException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ChangePasswordException.class)
    public ResponseEntity<Map<String, String>> handleChangePassword(ChangePasswordException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AcademicException.class)
    public ResponseEntity<Map<String, String>> handleAcademic(AcademicException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(EnvironmentException.class)
    public ResponseEntity<Map<String, String>> handleEnvironment(EnvironmentException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(FacialEventException.class)
    public ResponseEntity<Map<String, String>> handleFacialEvent(FacialEventException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(LoginException.class)
    public ResponseEntity<Map<String, String>> handleLogin(LoginException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        LOGGER.warn("Payload JSON inválido o incompleto", ex);
        return ResponseEntity.badRequest().body(Map.of("message", "JSON inválido o payload incorrecto."));
    }

    @ExceptionHandler(UserManagementException.class)
    public ResponseEntity<Map<String, String>> handleUserManagement(UserManagementException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(UserConfigurationException.class)
    public ResponseEntity<Map<String, String>> handleUserConfiguration(UserConfigurationException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        LOGGER.warn("Conflicto de integridad en base de datos", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", "El archivo contiene datos duplicados o inconsistentes. Revisa documentos, correos, programas y fichas."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        // El cliente recibe un mensaje estable, pero la causa queda registrada para diagnóstico.
        LOGGER.error("Error no controlado al procesar la solicitud", ex);
        return ResponseEntity.internalServerError().body(Map.of("message", "Error interno del servidor"));
    }
}
