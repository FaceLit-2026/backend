package com.FaceLit.backend.auth.dto.request.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestPasswordRecoveryDTO {

     // Correo del usuario que olvidó su contraseña
    // Debe existir en BD — si no, el sistema rechaza con "Correo no registrado"
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de correo inválido")
    private String email;

}
