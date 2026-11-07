package com.FaceLit.backend.auth.service.serviceImpl.security;

import java.time.OffsetDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.FaceLit.backend.auth.dto.request.security.RequestPasswordRecoveryDTO;
import com.FaceLit.backend.auth.dto.request.security.ResetPasswordDTO;
import com.FaceLit.backend.auth.dto.request.security.VerifyTokenDTO;
import com.FaceLit.backend.auth.dto.response.security.PasswordRecoveryResponseDTO;
import com.FaceLit.backend.auth.exception.PasswordRecoveryException;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.PasswordRecovery;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.security.CredentialRepository;
import com.FaceLit.backend.auth.repository.security.PasswordRecoveryRepository;
import com.FaceLit.backend.auth.service.security.PasswordRecoveryService;
import com.FaceLit.backend.shared.constants.AppConstants;
import com.FaceLit.backend.shared.service.EmailService;
import com.FaceLit.backend.shared.util.VerificationCodeGenerator;

import jakarta.transaction.Transactional;

@Service
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    private final CredentialRepository credentialRepository;
    private final PasswordRecoveryRepository passwordRecoveryRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final VerificationCodeGenerator verificationCodeGenerator;
    private final com.FaceLit.backend.notification.service.NotificationService notifications;
    private final RecoveryChallengeService challenges;
    private final SessionSecurityService sessions;
    private AccountAccessService accountAccess;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Autowired
    public void setAccountAccess(AccountAccessService accountAccess) { this.accountAccess = accountAccess; }

    public PasswordRecoveryServiceImpl(
            CredentialRepository credentialRepository,
            PasswordRecoveryRepository passwordRecoveryRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            VerificationCodeGenerator verificationCodeGenerator,
            com.FaceLit.backend.notification.service.NotificationService notifications, RecoveryChallengeService challenges, SessionSecurityService sessions) {
        this.credentialRepository = credentialRepository;
        this.passwordRecoveryRepository = passwordRecoveryRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.verificationCodeGenerator = verificationCodeGenerator;
        this.notifications = notifications;
        this.challenges = challenges; this.sessions = sessions;
    }

    @Override
    @Transactional
    public PasswordRecoveryResponseDTO requestRecovery(RequestPasswordRecoveryDTO dto) {

        // 1. Buscar las credenciales por email
        // Si no existe → se rechaza explícitamente (decisión de negocio confirmada)
        Credential credential = credentialRepository.findByEmailIgnoreCase(dto.getEmail().trim()).orElse(null);
        if (credential == null) throw new PasswordRecoveryException("Correo no registrado");

        if (entityManager != null) {
            credential = credentialRepository.lockByUserId(credential.getUser().getIdUser()).orElseThrow();
            entityManager.refresh(credential);
            if (!credential.getEmail().equalsIgnoreCase(dto.getEmail().trim()))
                throw new PasswordRecoveryException("Correo no registrado");
        }

        User user = credential.getUser();

        // 2 Validar que la cuenta esté ACTIVA
        if (user.getAccountStatus() !=AccountStatus.ACTIVE) {
            throw new PasswordRecoveryException("La cuenta no se encuentra habilitada para recuperar contraseña"); 
        }

        //  2.1 Invalidar el código anterior si existe — igual que resendCode() en RegisterServiceImpl
        var previousCodes = passwordRecoveryRepository.findAllByUser_IdUser(user.getIdUser());
        var now = OffsetDateTime.now();
        for (var previous : previousCodes) {
            if (!previous.isUsed() && previous.getExpirationDate().isAfter(now)
                    && previous.getVerificationAttempts() >= challenges.maxAttempts())
                throw new com.FaceLit.backend.auth.exception.RecoverySecurityException(429,
                    "Recuperación bloqueada. Espera a que venza antes de solicitar otro código.",
                    Math.max(1, java.time.Duration.between(now, previous.getExpirationDate()).toSeconds()));
        }
        previousCodes.forEach(previous -> previous.setUsed(true));
        passwordRecoveryRepository.saveAll(previousCodes);

        // 3. Generar el código numérico de seis dígitos.
        String code = verificationCodeGenerator.generate();
        int retries = 0;
        while (passwordRecoveryRepository.existsByToken(code)) {
            if (++retries >= 10) throw new PasswordRecoveryException("No fue posible generar un codigo. Intenta nuevamente.");
            code = verificationCodeGenerator.generate();
        }

        // 4. Guardar el nuevo registro de recuperación con expiración de 5 minutos
        PasswordRecovery recovery = new PasswordRecovery();
        recovery.setUser(user);
        recovery.setToken(code);
        recovery.setDestinationEmail(credential.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
        recovery.setExpirationDate(OffsetDateTime.now()
            .plusMinutes(AppConstants.PASSWORD_RECOVERY_EXPIRY_MINUTES));
        recovery.setUsed(false);
        passwordRecoveryRepository.save(recovery);

        // 5. Enviar el código al correo del usuario
        emailService.sendRecoveryCode(credential.getEmail(), code);

        // 6. Responder confirmando el envío
        return PasswordRecoveryResponseDTO.codeSent();
    }

    @Override
    public PasswordRecoveryResponseDTO verifyToken(VerifyTokenDTO dto) {
        return PasswordRecoveryResponseDTO.authorized(challenges.verify(dto.getEmail(), dto.getToken()));
    }

    @Override
    @Transactional
    public PasswordRecoveryResponseDTO resetPassword(ResetPasswordDTO dto) {

        PasswordRecovery recovery = challenges.authorizeReset(dto.getEmail(), dto.getToken());

        // 3. Verificar que las contraseñas coincidan
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new PasswordRecoveryException("Las contraseñas no coinciden");
        }

        // 4. Obtener el usuario y su credencial
        User user = recovery.getUser();
        Credential credential = entityManager == null ? user.getCredential()
                : credentialRepository.findByUser(user)
                        .orElseThrow(() -> new PasswordRecoveryException("El usuario no tiene credenciales"));
        if (accountAccess != null) accountAccess.completeEmailChange(recovery, credential);

        // 5. Hashear y actualizar la contraseña
        // La contraseña anterior se sobreescribe — deja de ser válida automáticamente
        credential.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setInitialPasswordChanged(true);
        // Reseteamos también los intentos fallidos — el usuario recupera acceso limpio
        credential.setFailedAttempts(0);
        credentialRepository.save(credential);

        // 6. Marcar el código como usado — no puede reutilizarse
        recovery.setUsed(true);
        recovery.setResetTokenHash(null);
        sessions.revokeAll(user.getIdUser());
        passwordRecoveryRepository.save(recovery);

        credential.setLockedUntil(null);
        if (accountAccess != null) accountAccess.cancelWelcome(user);
        String recipientName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName())).trim();
        var metadata = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        metadata.put("action", "password_recovered"); metadata.put("recipientName", recipientName);
        metadata.put("recipientContext", "account_owner"); metadata.put("occurredAt", OffsetDateTime.now().toString());
        notifications.createForRecipient(new com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO(
                user.getIdUser(), "password_changed", "Contraseña restablecida",
                "Destinatario: " + recipientName + ". Tu contraseña fue restablecida mediante recuperación. Fecha: "
                        + OffsetDateTime.now().atZoneSameInstant(java.time.ZoneId.of("America/Bogota")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        + " (hora de Colombia). Recibes este aviso para confirmar un cambio de seguridad de tu cuenta. Ya puedes ingresar con tu nueva contraseña. Si no realizaste este cambio, contacta al coordinador de inmediato.",
                user.getIdUser(), "user_app", null, metadata.toString()));

        // 7. Responder con éxito
        return PasswordRecoveryResponseDTO.passwordReset();
    }

}
