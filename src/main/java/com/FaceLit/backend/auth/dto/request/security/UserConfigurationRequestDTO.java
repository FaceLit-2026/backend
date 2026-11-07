package com.FaceLit.backend.auth.dto.request.security;

import com.FaceLit.backend.auth.model.enums.Language;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserConfigurationRequestDTO {

    // true = activadas, false = desactivadas
    private boolean notificationsActive = true;

    // true = modo oscuro, false = modo claro
    private boolean darkMode = false;

    // Idioma: ES, EN, PR o FR
    @NotNull(message = "El idioma es obligatorio")
    private Language language;

}
