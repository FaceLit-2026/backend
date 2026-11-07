package com.FaceLit.backend.auth.service.serviceImpl.security;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.FaceLit.backend.auth.dto.request.security.ChangePasswordRequestDTO;
import com.FaceLit.backend.auth.dto.response.security.PasswordRecoveryResponseDTO;
import com.FaceLit.backend.auth.exception.ChangePasswordException;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.auth.service.security.ChangePasswordService;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;

import jakarta.transaction.Transactional;

@Service
public class ChangePasswordServiceImpl implements ChangePasswordService {
    private final com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService sessions;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private AccountAccessService accountAccess;
    @org.springframework.beans.factory.annotation.Autowired
    public void setAccountAccess(AccountAccessService accountAccess) { this.accountAccess = accountAccess; }

    public ChangePasswordServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService, com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService sessions) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.sessions = sessions;
    }

    @Override
    @Transactional
    public PasswordRecoveryResponseDTO changePassword(UUID userId, ChangePasswordRequestDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new ChangePasswordException("Las contraseñas no coinciden");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ChangePasswordException("Usuario no encontrado"));
        Credential credential = user.getCredential();
        if (credential == null) {
            throw new ChangePasswordException("El usuario no tiene credenciales configuradas");
        }

        credential.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setInitialPasswordChanged(true);
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        if (accountAccess != null) accountAccess.cancelWelcome(user);
        sessions.revokeAll(userId);
        notifyPasswordChanged(user);
        return PasswordRecoveryResponseDTO.passwordReset();
    }

    private void notifyPasswordChanged(User user) {
        try {
            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    user.getIdUser(),
                    "password_changed",
                    "Tu contrasena fue modificada",
                    "Tu contrasena de FaceLit fue modificada correctamente. Si no reconoces este cambio, contacta a coordinacion.",
                    user.getIdUser(),
                    "user",
                    null,
                    "{\"entityType\":\"user\",\"entityId\":\"" + user.getIdUser() + "\",\"action\":\"password_changed\"}"));
        } catch (RuntimeException ignored) {
            // El cambio de contrasena no debe fallar por una notificacion informativa.
        }
    }
}
