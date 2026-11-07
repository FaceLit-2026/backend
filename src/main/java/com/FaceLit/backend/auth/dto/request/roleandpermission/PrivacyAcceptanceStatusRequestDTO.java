package com.FaceLit.backend.auth.dto.request.roleandpermission;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrivacyAcceptanceStatusRequestDTO {

    @NotBlank(message = "Debes indicar un número de documento.")
    @Pattern(regexp = "^\\d{6,15}$", message = "El número de documento no es válido.")
    @JsonAlias({"documentNumber", "documento", "numeroDocumento", "numero_documento"})
    private String numberDocument;
}
