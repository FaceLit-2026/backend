package com.FaceLit.backend.auth.service.security;
import java.util.UUID;

import com.FaceLit.backend.auth.dto.request.security.UserConfigurationRequestDTO;
import com.FaceLit.backend.auth.dto.response.security.UserConfigurationResponseDTO;


public interface UserConfigurationService {

     // Crea la configuracion la primera vez que el usuario entra a configuracion
    UserConfigurationResponseDTO createConfiguration(
            UUID userId, UserConfigurationRequestDTO dto);

    // Actualiza la configuracion existente
    UserConfigurationResponseDTO updateConfiguration(
            UUID userId, UserConfigurationRequestDTO dto);

    // Consulta la configuracion del usuario
    UserConfigurationResponseDTO getConfiguration(UUID userId);


}
