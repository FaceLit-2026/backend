package com.FaceLit.backend.auth.service.serviceImpl.security;

import com.FaceLit.backend.auth.dto.request.security.CompleteAccountAccessDTO;
import com.FaceLit.backend.auth.exception.PasswordRecoveryException;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.enums.CredentialStatus;
import com.FaceLit.backend.auth.model.security.*;
import com.FaceLit.backend.auth.repository.security.*;
import com.FaceLit.backend.auth.service.security.InitialCredentialsEmailRequested;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;
import com.FaceLit.backend.shared.service.EmailService;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable credential-email delivery. Passwords are generated in memory and stored only as hashes. */
@Service
public class AccountAccessService {
    private final com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService sessions;
    private static final Logger log = LoggerFactory.getLogger(AccountAccessService.class);
    private final AccountAccessRequestRepository requests;
    private final CredentialRepository credentials;
    private final PasswordRecoveryRepository recoveries;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final NotificationService notifications;
    private final String accessUrl;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @org.springframework.beans.factory.annotation.Autowired
    private com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository userRoles;
    @org.springframework.beans.factory.annotation.Autowired
    private InitialCredentialEmailTemplate welcomeTemplate;

    public AccountAccessService(AccountAccessRequestRepository requests, CredentialRepository credentials,
            PasswordRecoveryRepository recoveries, PasswordEncoder encoder, EmailService email,
            ApplicationEventPublisher events, TransactionTemplate transactions, NotificationService notifications,
            @Value("${facelit.access-url:http://localhost:8081}") String accessUrl, com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService sessions) {
        this.requests = requests; this.credentials = credentials; this.recoveries = recoveries;
        this.encoder = encoder; this.email = email; this.events = events; this.transactions = transactions;
        this.notifications = notifications; this.accessUrl = accessUrl.replaceAll("/+$", "");
        this.sessions = sessions;
    }

    @Transactional
    public void welcome(User user, Credential credential) {
        welcome(user, credential, null);
    }

    @Transactional
    public void welcome(User user, Credential credential, String rawPassword) {
        queue(user, credential, credential.getEmail(), "WELCOME", rawPassword, credential.getEmail());
    }

    @Transactional
    public void changeEmail(User user, Credential credential, String newEmail) {
        String target = normalize(newEmail);
        if (!target.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new PasswordRecoveryException("El correo electronico no es valido");
        Credential current = credentials.lockByUserId(user.getIdUser()).orElseThrow();
        if (current.getEmail().equalsIgnoreCase(target)) {
            retryWelcome(user);
            return;
        }
        credentials.findByEmailIgnoreCase(target).filter(c -> !c.getUser().getIdUser().equals(user.getIdUser()))
                .ifPresent(c -> { throw new PasswordRecoveryException("Ya existe un usuario con ese correo"); });
        // Administrative changes take effect on this same account immediately.
        // Invalidate every recovery code addressed to the previous mailbox.
        String previousEmail = current.getEmail();
        updateEmailAndAudit(current, target);
        credentials.saveAndFlush(current);
        var oldCodes = recoveries.findAllByUser_IdUser(user.getIdUser());
        oldCodes.forEach(r -> r.setUsed(true)); recoveries.saveAll(oldCodes);
        queue(user, current, target, "EMAIL_CHANGE", null, previousEmail);
    }

    private void updateEmailAndAudit(Credential credential, String target) {
        if (!credential.getEmail().equalsIgnoreCase(target)) {
            credential.setEmail(target);
            credential.getUser().setUpdatedAt(OffsetDateTime.now());
        }
    }

    private void queue(User user, Credential credential, String target, String purpose, String rawPassword, String originalEmail) {
        List<AccountAccessRequest> pending = requests.findByUser_IdUserAndUsedFalse(user.getIdUser());
        for (AccountAccessRequest request : pending) {
            if (request.getPurpose().equals(purpose) && request.getTargetEmail().equalsIgnoreCase(target)) {
                retry(request, false);
                return;
            }
        }
        // A newer administrative email correction supersedes earlier unverified destinations.
        if ("EMAIL_CHANGE".equals(purpose)) {
            pending.forEach(r -> { r.setUsed(true); r.setSendStatus("CANCELLED"); });
            requests.saveAll(pending);
            requests.flush();
        }
        AccountAccessRequest request = new AccountAccessRequest();
        request.setUser(user); request.setPurpose(purpose); request.setTargetEmail(normalize(target));
        request.setOriginalEmail(originalEmail);
        requests.saveAndFlush(request);
        events.publishEvent(new InitialCredentialsEmailRequested(request.getId(), rawPassword));
    }

    /** CSV without changes only retries a failed delivery; it never creates another one. */
    @Transactional
    public void retryWelcome(User user) {
        requests.findByUser_IdUserAndUsedFalse(user.getIdUser()).stream()
                .forEach(r -> retry(r, false));
    }

    @Transactional
    public void cancelWelcome(User user) {
        var pending = requests.findByUser_IdUserAndUsedFalse(user.getIdUser());
        // A user-chosen recovery password must never be overwritten by a delayed credential email.
        pending.forEach(r -> { r.setUsed(true); r.setSendStatus("CANCELLED"); });
        requests.saveAll(pending);
    }

    private void retry(AccountAccessRequest request, boolean explicit) {
        OffsetDateTime now = OffsetDateTime.now();
        if (request.getLastAttemptAt() != null && request.getLastAttemptAt().isAfter(now.minusMinutes(1))) return;
        boolean expired = request.getExpiresAt() != null && request.getExpiresAt().isBefore(now);
        if (explicit || expired || "FAILED".equals(request.getSendStatus())) {
            request.setAttempts(0); request.setSendStatus("PENDING");
            requests.save(request);
            events.publishEvent(new InitialCredentialsEmailRequested(request.getId()));
        }
    }

    /** Resolve a pending address after the user enters it in password recovery. */
    @Transactional
    public Optional<AccountAccessRequest> pendingEmailChange(String address) {
        if (credentials.findByEmailIgnoreCase(normalize(address)).isPresent()) return Optional.empty();
        List<AccountAccessRequest> pending = requests.findByTargetEmailIgnoreCaseAndUsedFalse(normalize(address));
        if (pending.stream().filter(r -> "EMAIL_CHANGE".equals(r.getPurpose())).count() > 1)
            throw new PasswordRecoveryException("El correo tiene varias solicitudes pendientes. Contacta a coordinacion.");
        for (AccountAccessRequest candidate : pending) {
            if (!"EMAIL_CHANGE".equals(candidate.getPurpose())) continue;
            Credential credential = credentials.lockByUserId(candidate.getUser().getIdUser()).orElseThrow();
            AccountAccessRequest request = requests.lockById(candidate.getId()).orElseThrow();
            entityManager.refresh(credential);
            entityManager.refresh(request);
            if (!request.isUsed() && credential.getEmail().equalsIgnoreCase(request.getOriginalEmail())
                    && credential.getUser().getAccountStatus() == AccountStatus.ACTIVE) {
                return Optional.of(request);
            }
        }
        return Optional.empty();
    }

    /** An issued code is bound to one immutable destination, even after a subsequent correction. */
    @Transactional
    public void validateEmailChangeCode(PasswordRecovery recovery, boolean requireVerified) {
        if (recovery.getAccessRequest() == null) return;
        Credential credential = credentials.lockByUserId(recovery.getUser().getIdUser()).orElseThrow();
        AccountAccessRequest request = requests.lockById(recovery.getAccessRequest().getId()).orElseThrow();
        entityManager.refresh(credential); entityManager.refresh(request);
        if (request.isUsed() || !"EMAIL_CHANGE".equals(request.getPurpose())
                || !request.getUser().getIdUser().equals(recovery.getUser().getIdUser())
                || !credential.getEmail().equalsIgnoreCase(request.getOriginalEmail()))
            throw new PasswordRecoveryException("La solicitud de cambio de correo ya no esta vigente. Solicita otro codigo.");
        if (credential.getUser().getAccountStatus() != AccountStatus.ACTIVE
                || credential.getCredentialStatus() == CredentialStatus.INACTIVE)
            throw new PasswordRecoveryException("La cuenta no se encuentra habilitada");
        if (requireVerified && recovery.getVerifiedAt() == null)
            throw new PasswordRecoveryException("Debes verificar el codigo de seis digitos antes de cambiar la contrasena");
        credentials.findByEmailIgnoreCase(request.getTargetEmail())
                .filter(c -> !c.getUser().getIdUser().equals(recovery.getUser().getIdUser()))
                .ifPresent(c -> { throw new PasswordRecoveryException("Ya existe un usuario con ese correo"); });
    }

    @Transactional
    public void completeEmailChange(PasswordRecovery recovery, Credential credential) {
        if (recovery.getAccessRequest() == null) return;
        validateEmailChangeCode(recovery, true);
        updateEmailAndAudit(credential, recovery.getAccessRequest().getTargetEmail());
        var pending = requests.findByUser_IdUserAndUsedFalse(recovery.getUser().getIdUser());
        pending.forEach(r -> { r.setUsed(true); if (!r.getId().equals(recovery.getAccessRequest().getId())) r.setSendStatus("CANCELLED"); });
        requests.saveAll(pending);
        var oldCodes = recoveries.findAllByUser_IdUser(recovery.getUser().getIdUser());
        oldCodes.forEach(r -> r.setUsed(true)); recoveries.saveAll(oldCodes);
    }

    @Scheduled(fixedDelay = 60_000)
    public void retryPendingDeliveries() {
        requests.findDeliveryCandidates().forEach(this::dispatch);
    }

    public void dispatch(UUID requestId) { dispatch(requestId, null); }

    public void dispatch(UUID requestId, String suppliedPassword) {
        transactions.executeWithoutResult(tx -> {
            AccountAccessRequest candidate = requests.findById(requestId).orElse(null);
            if (candidate == null) return;
            // Same lock order as profile updates and recovery, so a stale queued address cannot win.
            Credential credential = credentials.lockByUserId(candidate.getUser().getIdUser()).orElse(null);
            AccountAccessRequest request = requests.lockById(requestId).orElse(null);
            if (request == null) return;
            entityManager.refresh(request);
            if (credential != null) entityManager.refresh(credential);
            if (request.isUsed() || request.getAttempts() >= 3
                    || "SENT".equals(request.getSendStatus())) return;
            if (credential == null || !credential.getEmail().equalsIgnoreCase(request.getTargetEmail())
                    || request.getUser().getAccountStatus() != AccountStatus.ACTIVE) {
                request.setUsed(true); request.setSendStatus("CANCELLED"); requests.save(request); return;
            }
            boolean emailChange = "EMAIL_CHANGE".equals(request.getPurpose());
            // On retry generate a fresh password: the original plaintext is intentionally not persisted.
            String password = suppliedPassword != null && encoder.matches(suppliedPassword, credential.getPassword())
                    ? suppliedPassword : InitialCredentialServiceImpl.newGeneratedPassword();
            request.setAttempts(request.getAttempts() + 1);
            request.setLastAttemptAt(OffsetDateTime.now());
            try {
                String role = userRoles.findByUserId(request.getUser().getIdUser())
                        .map(r -> com.FaceLit.backend.notification.service.ProfileChangeNotificationService.roleLabel(r.getRole().getNameRole().name()))
                        .orElse("Sin rol");
                email.sendInitialCredentials(request.getTargetEmail(), "FaceLit - Credenciales de acceso",
                        welcomeTemplate.render(request.getUser(), role, request.getTargetEmail(), password,
                                accessUrl + "/auth/login", accessUrl + "/auth/password-recovery", emailChange));
                // Failed SMTP attempts preserve the previous usable password.
                credential.setPassword(encoder.encode(password));
                sessions.revokeAll(credential.getUser().getIdUser());
                credential.setFailedAttempts(0); credential.setLockedUntil(null);
                credentials.save(credential);
                request.setTokenHash(null); request.setExpiresAt(null);
                request.setUsed(true); request.setSendStatus("SENT"); request.setLastError(null);
            } catch (Exception ex) {
                request.setSendStatus("FAILED");
                // Do not retain provider exception messages: they may contain the secret link or SMTP credentials.
                request.setLastError(ex.getClass().getSimpleName());
                log.warn("Fallo envio de instrucciones de acceso {} ({})", requestId, request.getLastError());
            }
            requests.save(request);
        });
    }

    @Transactional
    public void complete(CompleteAccountAccessDTO dto) {
        if (!Objects.equals(dto.getNewPassword(), dto.getConfirmPassword()))
            throw new PasswordRecoveryException("Las contrasenas no coinciden");
        AccountAccessRequest candidate = requests.findByTokenHash(digest(dto.getToken()))
                .orElseThrow(() -> new PasswordRecoveryException("Enlace incorrecto"));
        Credential credential = credentials.lockByUserId(candidate.getUser().getIdUser()).orElseThrow();
        AccountAccessRequest request = requests.lockById(candidate.getId()).orElseThrow();
        entityManager.refresh(credential);
        entityManager.refresh(request);
        if ("EMAIL_CHANGE".equals(request.getPurpose()))
            throw new PasswordRecoveryException("Para cambiar el correo debes ingresar tu nuevo correo y verificar el codigo de seis digitos en recuperacion de contrasena");
        if (request.isUsed() || !Objects.equals(request.getTokenHash(), digest(dto.getToken()))
                || request.getExpiresAt() == null || !request.getExpiresAt().isAfter(OffsetDateTime.now()))
            throw new PasswordRecoveryException("Enlace vencido o utilizado");
        if (credential.getUser().getAccountStatus() != AccountStatus.ACTIVE
                || credential.getCredentialStatus() == CredentialStatus.INACTIVE)
            throw new PasswordRecoveryException("La cuenta no se encuentra habilitada");
        if (!credential.getEmail().equalsIgnoreCase(request.getOriginalEmail()))
            throw new PasswordRecoveryException("El correo de la cuenta cambio; solicita otro enlace");
        credentials.findByEmailIgnoreCase(request.getTargetEmail())
                .filter(c -> !c.getUser().getIdUser().equals(credential.getUser().getIdUser()))
                .ifPresent(c -> { throw new PasswordRecoveryException("Ya existe un usuario con ese correo"); });
        updateEmailAndAudit(credential, request.getTargetEmail());
        credential.setPassword(encoder.encode(dto.getNewPassword()));
        credential.getUser().setInitialPasswordChanged(true);
        credential.setFailedAttempts(0); credential.setLockedUntil(null);
        sessions.revokeAll(credential.getUser().getIdUser());
        credentials.saveAndFlush(credential);
        // Consume every previously issued path, including recovery codes sent to the old email.
        var pending = requests.findByUser_IdUserAndUsedFalse(credential.getUser().getIdUser());
        pending.forEach(r -> { r.setUsed(true); if (!r.getId().equals(request.getId())) r.setSendStatus("CANCELLED"); });
        request.setUsed(true); requests.saveAll(pending); requests.save(request);
        var oldCodes = recoveries.findAllByUser_IdUser(credential.getUser().getIdUser());
        oldCodes.forEach(r -> r.setUsed(true)); recoveries.saveAll(oldCodes);
        notifications.createForRecipient(new CreateNotificationRequestDTO(credential.getUser().getIdUser(),
                "password_changed", "Acceso actualizado", "Estableciste una nueva contrasena. Tu correo vigente es "
                + credential.getEmail() + ". Si no realizaste este cambio, contacta a coordinacion.",
                credential.getUser().getIdUser(), "user_app", null, "{\"action\":\"account_access_completed\"}"));
    }

    static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
