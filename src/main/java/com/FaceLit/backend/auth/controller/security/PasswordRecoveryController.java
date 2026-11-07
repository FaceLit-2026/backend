package com.FaceLit.backend.auth.controller.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.auth.dto.request.security.RequestPasswordRecoveryDTO;
import com.FaceLit.backend.auth.dto.request.security.ResetPasswordDTO;
import com.FaceLit.backend.auth.dto.request.security.VerifyTokenDTO;
import com.FaceLit.backend.auth.dto.request.security.ChangePasswordRequestDTO;
import com.FaceLit.backend.auth.dto.response.security.PasswordRecoveryResponseDTO;
import com.FaceLit.backend.auth.service.security.ChangePasswordService;
import com.FaceLit.backend.auth.service.security.PasswordRecoveryService;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class PasswordRecoveryController {

    private final com.FaceLit.backend.auth.service.serviceImpl.security.RecoveryRateLimiter limiter;
    private final PasswordRecoveryService passwordRecoveryService;
    private final ChangePasswordService changePasswordService;
    private final com.FaceLit.backend.auth.service.serviceImpl.security.AccountAccessService accountAccess;

    public PasswordRecoveryController(PasswordRecoveryService passwordRecoveryService,
            ChangePasswordService changePasswordService,
            com.FaceLit.backend.auth.service.serviceImpl.security.AccountAccessService accountAccess,
            com.FaceLit.backend.auth.service.serviceImpl.security.RecoveryRateLimiter limiter) {
        this.passwordRecoveryService = passwordRecoveryService;
        this.changePasswordService = changePasswordService;
        this.accountAccess = accountAccess; this.limiter = limiter;
    }

    // PASO 1 — Solicitar recuperación: el usuario manda su correo
    // POST /api/auth/solicitar-recuperacion
    // Público — el usuario aún no tiene token
    @PostMapping("/request-recovery")
    public ResponseEntity<PasswordRecoveryResponseDTO> requestRecovery(
            @Valid @RequestBody RequestPasswordRecoveryDTO dto) {
        limiter.account(dto.getEmail(), "request");
        PasswordRecoveryResponseDTO response = passwordRecoveryService.requestRecovery(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/complete-access")
    public ResponseEntity<PasswordRecoveryResponseDTO> completeAccess(
            @Valid @RequestBody com.FaceLit.backend.auth.dto.request.security.CompleteAccountAccessDTO dto) {
        accountAccess.complete(dto);
        return ResponseEntity.ok(PasswordRecoveryResponseDTO.passwordReset());
    }

    @PostMapping("/verify-token")
    public ResponseEntity<PasswordRecoveryResponseDTO> verifyToken(
            @Valid @RequestBody VerifyTokenDTO dto) {
        limiter.account(dto.getEmail(), "verify");
        return ResponseEntity.ok(passwordRecoveryService.verifyToken(dto));
    }

    // PASO 2 — Restablecer contraseña: el usuario manda el código + nueva
    // contraseña
    // POST /api/auth/restablecer-contrasena
    // Público — el usuario aún no tiene token
    @PostMapping("/reset-password")
    public ResponseEntity<PasswordRecoveryResponseDTO> resetPassword(
            @Valid @RequestBody ResetPasswordDTO dto) {
        limiter.account(dto.getEmail(), "reset");
        PasswordRecoveryResponseDTO response = passwordRecoveryService.resetPassword(dto);
        return ResponseEntity.ok(response);

    }

    @PostMapping("/change-password")
    public ResponseEntity<PasswordRecoveryResponseDTO> changePassword(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ChangePasswordRequestDTO dto) {
        return ResponseEntity.ok(changePasswordService.changePassword(userId, dto));
    }
}
