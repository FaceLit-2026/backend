package com.FaceLit.backend.auth.controller.security;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.auth.dto.request.security.UserConfigurationRequestDTO;
import com.FaceLit.backend.auth.dto.response.security.UserConfigurationResponseDTO;
import com.FaceLit.backend.auth.service.security.UserConfigurationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/profile/configuration")
public class UserConfigurationController {

    private final UserConfigurationService userConfigurationService;

    public UserConfigurationController(
            UserConfigurationService userConfigurationService) {
        this.userConfigurationService = userConfigurationService;
    }

    // POST /api/apprentice/configuration
    // Crea la configuracion la primera vez
    // El userId se extrae del JWT — todos los roles pueden configurar
    @PostMapping
    public ResponseEntity<UserConfigurationResponseDTO> create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UserConfigurationRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userConfigurationService.createConfiguration(userId, dto));
    }

    // PUT /api/apprentice/configuration
    // Actualiza la configuracion existente
    @PutMapping
    public ResponseEntity<UserConfigurationResponseDTO> update(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UserConfigurationRequestDTO dto) {
        return ResponseEntity.ok(
                userConfigurationService.updateConfiguration(userId, dto));
    }

    // GET /api/apprentice/configuration
    // Consulta la configuracion del usuario autenticado
    @GetMapping
    public ResponseEntity<UserConfigurationResponseDTO> get(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(
                userConfigurationService.getConfiguration(userId));
    }
}
