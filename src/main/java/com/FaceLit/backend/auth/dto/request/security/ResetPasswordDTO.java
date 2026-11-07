package com.FaceLit.backend.auth.dto.request.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordDTO {
    @NotBlank(message = "El correo es obligatorio")
    @jakarta.validation.constraints.Email(message = "Correo inválido")
    private String email;


    // Autorización de un solo uso emitida tras verificar cuenta y código.
    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "Debes verificar el código antes de cambiar la contraseña")
    private String token;

    // Política de contraseña compartida por recuperación y cambio voluntario.
    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*]).{8,15}$",
        message = "La contraseña debe tener entre 8 y 15 caracteres, una mayúscula, un número y un símbolo"
    )
    private String newPassword;

    @NotBlank(message = "Debes confirmar la nueva contraseña")
    private String confirmPassword;
}