package com.FaceLit.backend.auth.dto.request.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequestDTO {

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*]).{8,15}$",
            message = "La contraseña debe tener entre 8 y 15 caracteres, una mayúscula, un número y un símbolo")
    private String newPassword;

    @NotBlank(message = "Debes confirmar la nueva contraseña")
    private String confirmPassword;
}