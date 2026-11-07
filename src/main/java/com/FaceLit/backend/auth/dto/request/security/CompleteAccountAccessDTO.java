package com.FaceLit.backend.auth.dto.request.security;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CompleteAccountAccessDTO {
    @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$")
    private String token;
    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*])\\S{8,15}$",
            message = "La contrasena debe tener entre 8 y 15 caracteres, una mayuscula, un numero y un simbolo")
    private String newPassword;
    @NotBlank private String confirmPassword;
}
