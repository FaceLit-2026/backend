package com.FaceLit.backend.auth.dto.response.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PasswordRecoveryResponseDTO {

    // Mensaje legible para el frontend
    private String message;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String resetToken;
    public PasswordRecoveryResponseDTO(String message) { this(message, null); }
    public static PasswordRecoveryResponseDTO authorized(String token) {
        return new PasswordRecoveryResponseDTO("Código válido", token);
    }

    // Se devuelve cuando el código de 6 dígitos fue enviado al correo
    public static PasswordRecoveryResponseDTO codeSent() {
        return new PasswordRecoveryResponseDTO("Se envió un código de recuperación a tu correo electrónico");

    }

    public static PasswordRecoveryResponseDTO tokenVerified() {
        return new PasswordRecoveryResponseDTO("Código válido");
    }

    // Se devuelve cuando la contraseña se actualizó correctamente
    public static PasswordRecoveryResponseDTO passwordReset() {
        return new PasswordRecoveryResponseDTO("Contraseña restablecida correctamente");

    }
}
