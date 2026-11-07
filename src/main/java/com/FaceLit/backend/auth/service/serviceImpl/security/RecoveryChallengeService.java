package com.FaceLit.backend.auth.service.serviceImpl.security;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import com.FaceLit.backend.auth.exception.RecoverySecurityException;
import com.FaceLit.backend.auth.model.security.PasswordRecovery;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.enums.CredentialStatus;
import com.FaceLit.backend.auth.repository.security.*;
import com.FaceLit.backend.auth.service.security.RecoverySecrets;

/** Serializes validation on the account lock shared by recovery and email changes. */
@Service
public class RecoveryChallengeService {
    private final CredentialRepository credentials;
    private final PasswordRecoveryRepository recoveries;
    private final int maxAttempts;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    public RecoveryChallengeService(CredentialRepository credentials, PasswordRecoveryRepository recoveries,
            @Value("${security.recovery.max-attempts:5}") int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("max-attempts must be positive");
        this.credentials = credentials; this.recoveries = recoveries; this.maxAttempts = maxAttempts;
    }
    @Transactional(noRollbackFor = RecoverySecurityException.class)
    public String verify(String email, String code) {
        PasswordRecovery recovery = current(email);
        if (recovery.getVerifiedAt() != null) throw new RecoverySecurityException(409, "Código ya verificado. Solicita otro código.");
        if (!MessageDigest.isEqual(recovery.getToken().getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
            recovery.setVerificationAttempts(recovery.getVerificationAttempts() + 1);
            recoveries.saveAndFlush(recovery);
            if (recovery.getVerificationAttempts() >= maxAttempts) throw blocked(recovery);
            throw new RecoverySecurityException(400, "Código incorrecto");
        }
        String authorization = RecoverySecrets.issue();
        recovery.setVerifiedAt(OffsetDateTime.now());
        recovery.setResetTokenHash(RecoverySecrets.digest(authorization));
        recoveries.saveAndFlush(recovery);
        return authorization;
    }
    @Transactional
    public PasswordRecovery authorizeReset(String email, String token) {
        PasswordRecovery recovery = current(email);
        if (recovery.getVerifiedAt() == null || !RecoverySecrets.matches(token, recovery.getResetTokenHash()))
            throw new RecoverySecurityException(400, "Autorización de recuperación inválida");
        return recovery;
    }
    private PasswordRecovery current(String email) {
        String address = email.trim().toLowerCase(Locale.ROOT);
        var candidate = credentials.findByEmailIgnoreCase(address)
                .orElseThrow(() -> new RecoverySecurityException(400, "Recuperación inválida"));
        var credential = credentials.lockByUserId(candidate.getUser().getIdUser()).orElseThrow();
        entityManager.refresh(credential);
        if (!credential.getEmail().equalsIgnoreCase(address) || credential.getUser().getAccountStatus() != AccountStatus.ACTIVE
                || credential.getCredentialStatus() == CredentialStatus.INACTIVE)
            throw new RecoverySecurityException(400, "Recuperación inválida");
        PasswordRecovery recovery = recoveries.findFirstByUser_IdUserAndUsedFalseOrderByCreatedAtDesc(credential.getUser().getIdUser())
                .orElseThrow(() -> new RecoverySecurityException(400, "Recuperación inválida"));
        entityManager.refresh(recovery);
        if (!address.equalsIgnoreCase(recovery.getDestinationEmail()))
            throw new RecoverySecurityException(400, "Recuperación inválida");
        if (!recovery.isCurrent()) throw new RecoverySecurityException(410, "Código vencido");
        if (recovery.getVerificationAttempts() >= maxAttempts) throw blocked(recovery);
        return recovery;
    }
    public int maxAttempts() { return maxAttempts; }
    private RecoverySecurityException blocked(PasswordRecovery recovery) {
        long seconds = Math.max(1, java.time.Duration.between(OffsetDateTime.now(), recovery.getExpirationDate()).toSeconds());
        return new RecoverySecurityException(429, "Se agotaron los intentos. Solicita otro código cuando termine el bloqueo.", seconds);
    }
}
