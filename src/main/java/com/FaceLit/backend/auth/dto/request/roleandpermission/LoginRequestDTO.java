package com.FaceLit.backend.auth.dto.request.roleandpermission;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

//  este ya es imolementacion del Correo y la contraeña
@Getter
@Setter
public class LoginRequestDTO {

    // El usuario se identifica con el documento registrado en user_app.
    @JsonAlias({"documentNumber", "documento", "numeroDocumento", "numero_documento"})
    private String numberDocument;

    // Acepta también el correo electrónico para compatibilidad con el frontend.
    @JsonAlias({"email", "correo", "mail"})
    private String email;

    // Se compara con el hash BCrypt guardado en Credential.password
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    // El backend registra la decisión de privacidad únicamente después de validar las credenciales.
    private boolean privacyAccepted;

    public String resolveIdentifier() {
        if (numberDocument != null && !numberDocument.isBlank()) {
            return numberDocument.trim();
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return null;
    }

    public void setEmail(String email) {
        this.email = email;
    }

}
