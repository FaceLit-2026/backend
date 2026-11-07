package com.FaceLit.backend.auth.controller.security;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.auth.dto.response.security.ProfileResponseDTO;
import com.FaceLit.backend.auth.service.security.ProfileService;

// No hace falta agregarlo a SecurityConfig — cae bajo
// anyRequest().authenticated(), así que solo exige un JWT válido,
// sin importar el rol (todos los roles pueden ver su propio perfil)

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

      private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    // GET /api/profile/me
    // Devuelve los datos completos del usuario autenticado
    @GetMapping("/me")
    public ResponseEntity<ProfileResponseDTO> getMyProfile(
            @AuthenticationPrincipal Object principal) {
        UUID userId = UUID.fromString(principal.toString());
        return ResponseEntity.ok(profileService.getMyProfile(userId));
    }

}

// api/profile/me -- APIN 

// REST