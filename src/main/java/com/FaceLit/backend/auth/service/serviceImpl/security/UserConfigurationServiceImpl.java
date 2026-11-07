package com.FaceLit.backend.auth.service.serviceImpl.security;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.FaceLit.backend.auth.dto.request.security.UserConfigurationRequestDTO;
import com.FaceLit.backend.auth.dto.response.security.UserConfigurationResponseDTO;
import com.FaceLit.backend.auth.exception.UserConfigurationException;
import com.FaceLit.backend.auth.model.security.UserConfiguration;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.auth.repository.security.UserConfigurationRepository;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.service.security.UserConfigurationService;

import java.util.UUID;

@Service
public class UserConfigurationServiceImpl implements UserConfigurationService {

    private final UserConfigurationRepository userConfigurationRepository;
    private final UserRepository userRepository;

    public UserConfigurationServiceImpl(
            UserConfigurationRepository userConfigurationRepository,
            UserRepository userRepository) {
        this.userConfigurationRepository = userConfigurationRepository;
        this.userRepository = userRepository;
    }

    private UserConfigurationResponseDTO toDTO(
            UserConfiguration config, String message) {
        return new UserConfigurationResponseDTO(
                config.getIdUserConfiguration(),
                config.getUser().getIdUser(),
                config.isNotificationsActive(),
                config.isDarkMode(),
                config.getLanguage(),
                message);
    }

    @Override
    @Transactional
    public UserConfigurationResponseDTO createConfiguration(
            UUID userId, UserConfigurationRequestDTO dto) {

        // 1. Verificar que el usuario existe
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserConfigurationException(
                        "Usuario no encontrado", HttpStatus.NOT_FOUND));

        // 2. Verificar que no tenga ya una configuracion
        // Solo se permite UNA configuracion por usuario
        if (userConfigurationRepository.existsByUser_IdUser(userId)) {
            throw new UserConfigurationException(
                    "Ya tienes una configuracion creada. Usa la opcion de actualizar",
                    HttpStatus.CONFLICT);
        }

        // 3. Crear la configuracion
        UserConfiguration config = new UserConfiguration();
        config.setUser(user);
        config.setNotificationsActive(dto.isNotificationsActive());
        config.setDarkMode(dto.isDarkMode());
        config.setLanguage(dto.getLanguage());

        UserConfiguration saved = userConfigurationRepository.save(config);

        return UserConfigurationResponseDTO.created(
                saved.getIdUserConfiguration(),
                saved.getUser().getIdUser(),
                saved.isNotificationsActive(),
                saved.isDarkMode(),
                saved.getLanguage());

    }

    @Override
    @Transactional
    public UserConfigurationResponseDTO updateConfiguration(
            UUID userId, UserConfigurationRequestDTO dto) {

        // 1. Buscar la configuracion del usuario
        UserConfiguration config = userConfigurationRepository
                .findByUser_IdUser(userId)
                .orElseThrow(() -> new UserConfigurationException(
                        "No tienes una configuracion creada. Crea una primero",
                        HttpStatus.NOT_FOUND));

        // 2. Actualizar los campos
        config.setNotificationsActive(dto.isNotificationsActive());
        config.setDarkMode(dto.isDarkMode());
        config.setLanguage(dto.getLanguage());

        UserConfiguration updated = userConfigurationRepository.save(config);

        return UserConfigurationResponseDTO.updated(
                updated.getIdUserConfiguration(),
                updated.getUser().getIdUser(),
                updated.isNotificationsActive(),
                updated.isDarkMode(),
                updated.getLanguage());
    }

    @Override
    public UserConfigurationResponseDTO getConfiguration(UUID userId) {
        UserConfiguration config = userConfigurationRepository
                .findByUser_IdUser(userId)
                .orElseThrow(() -> new UserConfigurationException(
                        "No tienes una configuracion creada todavia",
                        HttpStatus.NOT_FOUND));

        return toDTO(config, null);
    }

}
