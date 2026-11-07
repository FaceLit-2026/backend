package com.FaceLit.backend.auth.dto.response.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.auth.model.enums.Language;

@Getter
@AllArgsConstructor
public class UserConfigurationResponseDTO {

    private UUID idUserConfiguration;
    private UUID idUser;
    private boolean notificationsActive;
    private boolean darkMode;
    private Language language;
    private String message;

    public static UserConfigurationResponseDTO created(
            UUID idUserConfiguration, UUID idUser,
            boolean notificationsActive, boolean darkMode,
            Language language) {
        return new UserConfigurationResponseDTO(
                idUserConfiguration, idUser,
                notificationsActive, darkMode,
                language,
                "Configuracion creada correctamente");
    }

    public static UserConfigurationResponseDTO updated(
            UUID idUserConfiguration, UUID idUser,
            boolean notificationsActive, boolean darkMode,
            Language language) {
        return new UserConfigurationResponseDTO(
                idUserConfiguration, idUser,
                notificationsActive, darkMode,
                language,
                "Configuracion actualizada correctamente");
    }

}
