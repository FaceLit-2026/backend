package com.FaceLit.backend.auth.controller.roleandpermission;

import com.FaceLit.backend.auth.dto.request.roleandpermission.LoginRequestDTO;
import com.FaceLit.backend.auth.dto.request.roleandpermission.PasswordVerificationRequestDTO;
import com.FaceLit.backend.auth.dto.request.roleandpermission.PrivacyAcceptanceStatusRequestDTO;
import com.FaceLit.backend.auth.dto.response.roleandpermission.LoginResponseDTO;
import com.FaceLit.backend.auth.service.roleandpermission.LoginService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class LoginController {

     private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    // POST /api/auth/login
    // Público — el usuario aún no tiene token
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        LoginResponseDTO response = loginService.login(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-password")
    public ResponseEntity<Boolean> verifyPassword(
            @Valid @RequestBody PasswordVerificationRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(loginService.verifyPassword(UUID.fromString(principal.toString()), dto.getPassword()));
    }

    @PostMapping("/privacy-acceptance-status")
    public ResponseEntity<Boolean> privacyAcceptanceStatus(
            @Valid @RequestBody PrivacyAcceptanceStatusRequestDTO dto) {
        return ResponseEntity.ok(loginService.hasAcceptedPrivacy(dto.getNumberDocument().trim()));
    }
}
