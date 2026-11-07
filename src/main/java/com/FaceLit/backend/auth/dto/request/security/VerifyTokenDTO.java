package com.FaceLit.backend.auth.dto.request.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyTokenDTO {
    @NotBlank(message = "El correo es obligatorio")
    @jakarta.validation.constraints.Email(message = "Correo inválido")
    private String email;


    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "^[0-9]{6}$", message = "El código debe tener exactamente 6 dígitos numéricos")
    private String token;
}