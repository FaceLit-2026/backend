package com.FaceLit.backend.auth.service.serviceImpl.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;

import com.FaceLit.backend.auth.dto.request.security.ChangePasswordRequestDTO;
import com.FaceLit.backend.auth.dto.request.security.CoordinatorBootstrapRequestDTO;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.enums.CredentialStatus;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.roleandpermission.Role;
import com.FaceLit.backend.auth.model.roleandpermission.UserRole;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.roleandpermission.RoleRepository;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.auth.repository.security.CredentialRepository;
import com.FaceLit.backend.auth.repository.security.PasswordRecoveryRepository;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.auth.service.security.CoordinatorBootstrapService;
import jakarta.validation.Validator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoordinatorBootstrapServiceImpl implements CoordinatorBootstrapService {
    // SHA-256 fingerprint of the retired seed's bcrypt value. It cannot authenticate a user.
    private static final String PUBLISHED_FINGERPRINT = "fce6d028b4a0629d245cb13c931c4ff89e17996111348fb2e34c6f7c6be8fde1";
    private static final String AUDIT_ACTOR = "coordinator-bootstrap";
    private final UserRepository users;
    private final CredentialRepository credentials;
    private final RoleRepository roles;
    private final UserRoleRepository assignments;
    private final PasswordRecoveryRepository recoveries;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final SessionSecurityService sessions;
    private final AccountAccessService access;

    public CoordinatorBootstrapServiceImpl(UserRepository users, CredentialRepository credentials,
            RoleRepository roles, UserRoleRepository assignments, PasswordRecoveryRepository recoveries,
            PasswordEncoder encoder, Validator validator, SessionSecurityService sessions, AccountAccessService access) {
        this.users = users; this.credentials = credentials; this.roles = roles; this.assignments = assignments;
        this.recoveries = recoveries; this.encoder = encoder; this.validator = validator;
        this.sessions = sessions; this.access = access;
    }

    @Override
    @Transactional
    public Outcome initialize(CoordinatorBootstrapRequestDTO request) {
        validate(request);
        // Serializes simultaneous initializations without adding another table or application lock.
        Role role = roles.lockByNameRole(RoleName.COORDINATOR)
                .filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> failure("Aplica las migraciones de roles antes de inicializar el coordinador."));
        User user = users.findByDocumentNumber(request.document()).orElse(null);
        if (user != null) return existing(user, request);
        if (request.rotatePassword()) throw failure("El coordinador indicado no existe; no se creó ninguna cuenta.");
        if (!assignments.findByRoleName(RoleName.COORDINATOR).isEmpty()) {
            throw failure("Ya existe un coordinador. Usa su documento y la rotación explícita; no se creará otro.");
        }
        if (blank(request.firstName()) || blank(request.lastName())) throw failure("Nombre y apellido son obligatorios para crear el coordinador.");
        if (credentials.existsByEmailIgnoreCase(request.email().trim())) throw failure("El correo ya pertenece a una cuenta.");

        user = new User(); user.setDocumentNumber(request.document()); user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim()); user.setAccountStatus(AccountStatus.ACTIVE); user.setCreatedBy(AUDIT_ACTOR);
        users.save(user);
        Credential credential = new Credential(); credential.setUser(user); credential.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        credential.setPassword(encoder.encode(request.password())); credential.setCredentialStatus(CredentialStatus.ACTIVE);
        user.setInitialPasswordChanged(false);
        credential.setCreatedBy(AUDIT_ACTOR); credentials.save(credential); user.setCredential(credential);
        UserRole assignment = new UserRole(); assignment.setUser(user); assignment.setRole(role);
        assignment.setAssignedAt(OffsetDateTime.now()); assignment.setCreatedBy(AUDIT_ACTOR); assignments.save(assignment);
        return Outcome.CREATED;
    }

    private Outcome existing(User user, CoordinatorBootstrapRequestDTO request) {
        boolean coordinator = assignments.findByUserId(user.getIdUser())
                .filter(link -> link.getDeletedAt() == null && link.getRole().getNameRole() == RoleName.COORDINATOR).isPresent();
        if (!coordinator || user.getDeletedAt() != null || user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw failure("La operación requiere un coordinador existente y activo; no se cambió su rol ni su estado.");
        }
        Credential credential = credentials.lockByUserId(user.getIdUser())
                .filter(value -> value.getDeletedAt() == null && value.getCredentialStatus() == CredentialStatus.ACTIVE)
                .orElseThrow(() -> failure("La credencial no está activa; requiere revisión administrativa."));
        if (!credential.getEmail().equalsIgnoreCase(request.email().trim())) throw failure("El correo no coincide con el del coordinador.");
        if (encoder.matches(request.password(), credential.getPassword())) return Outcome.UNCHANGED;
        if (!request.rotatePassword()) throw failure("La cuenta ya existe. Habilita la rotación explícita para cambiar su contraseña.");

        credential.setPassword(encoder.encode(request.password())); credential.setFailedAttempts(0); credential.setLockedUntil(null);
        user.setInitialPasswordChanged(true);
        credential.setUpdatedBy(AUDIT_ACTOR); credentials.save(credential);
        sessions.revokeAll(user.getIdUser());
        recoveries.invalidateAllForUser(user.getIdUser());
        access.cancelWelcome(user);
        return Outcome.ROTATED;
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyNoPublishedCredential() {
        boolean exposed = assignments.findByRoleName(RoleName.COORDINATOR).stream()
                .filter(link -> link.getDeletedAt() == null)
                .map(UserRole::getUser)
                .filter(user -> user.getDeletedAt() == null && user.getAccountStatus() == AccountStatus.ACTIVE)
                .map(User::getCredential)
                .filter(value -> value != null && value.getDeletedAt() == null && value.getCredentialStatus() == CredentialStatus.ACTIVE)
                .anyMatch(value -> PUBLISHED_FINGERPRINT.equals(fingerprint(value.getPassword())));
        if (exposed) throw failure("La credencial del seed retirado sigue presente. Ejecuta la rotación explícita antes de iniciar FaceLit.");
    }

    private void validate(CoordinatorBootstrapRequestDTO request) {
        if (request == null || !validator.validate(request).isEmpty()) throw failure("Revisa los campos de configuración del coordinador; no se guardó ningún cambio.");
        // Reuse the application's existing password policy without logging rejected values.
        ChangePasswordRequestDTO password = new ChangePasswordRequestDTO();
        password.setNewPassword(request.password()); password.setConfirmPassword(request.password());
        if (!validator.validate(password).isEmpty()) throw failure("La contraseña debe cumplir la política actual de cambio de contraseña.");
    }

    private static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 no disponible", impossible); }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static IllegalStateException failure(String message) { return new IllegalStateException(message); }
}
