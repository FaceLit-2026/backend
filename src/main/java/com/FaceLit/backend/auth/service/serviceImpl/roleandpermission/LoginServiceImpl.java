package com.FaceLit.backend.auth.service.serviceImpl.roleandpermission;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.FaceLit.backend.auth.dto.request.roleandpermission.LoginRequestDTO;
import com.FaceLit.backend.auth.dto.response.roleandpermission.LoginResponseDTO;
import com.FaceLit.backend.auth.exception.LoginException;
import com.FaceLit.backend.auth.model.legal.AcceptanceTerms;
import com.FaceLit.backend.auth.model.legal.PrivacyNoticePolicy;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.auth.model.roleandpermission.UserRole;
import com.FaceLit.backend.auth.model.roleandpermission.RolePermission;
import com.FaceLit.backend.auth.repository.legal.AcceptanceTermsRepository;
import com.FaceLit.backend.auth.repository.security.CredentialRepository;
import com.FaceLit.backend.auth.repository.security.UserSessionRepository;
import com.FaceLit.backend.auth.repository.roleandpermission.RolePermissionRepository;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.auth.service.roleandpermission.JwtService;
import com.FaceLit.backend.auth.service.roleandpermission.LoginService;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;
import com.FaceLit.backend.shared.constants.AppConstants;

import java.util.List;

@Service
public class LoginServiceImpl implements LoginService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final CredentialRepository credentialRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NotificationService notificationService;
    private final AcceptanceTermsRepository acceptanceTermsRepository;
    private final UserSessionRepository userSessionRepository;

    public LoginServiceImpl(
            CredentialRepository credentialRepository,
            UserRoleRepository userRoleRepository,
            RolePermissionRepository rolePermissionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            NotificationService notificationService,
            AcceptanceTermsRepository acceptanceTermsRepository,
            UserSessionRepository userSessionRepository) {
        this.credentialRepository = credentialRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.notificationService = notificationService;
        this.acceptanceTermsRepository = acceptanceTermsRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    @Transactional(noRollbackFor = LoginException.class)
    public LoginResponseDTO login(LoginRequestDTO dto) {

        String identifier = dto.resolveIdentifier();
        if (identifier == null || identifier.isBlank()) {
            throw new LoginException("Debes indicar un documento o correo válido.");
        }

        Credential credential = credentialRepository.findByUser_DocumentNumber(identifier)
            .or(() -> credentialRepository.findByEmailIgnoreCase(identifier))
            .orElseThrow(() -> new LoginException("Usuario o contraseña incorrectos"));

        credential = credentialRepository.lockByUserId(credential.getUser().getIdUser()).orElseThrow();
        entityManager.refresh(credential);
        entityManager.refresh(credential.getUser());
        // 3. Obtener el usuario asociado a esa credencial
        User user = credential.getUser();

        if (credential.getLockedUntil() != null
                && credential.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new LoginException("La cuenta está bloqueada temporalmente");
        }

        if (credential.getLockedUntil() != null) {
            credential.setLockedUntil(null);
            credential.setFailedAttempts(0);
        }

        // Verificar que la cuenta esté ACTIVE
        // BLOCKED = demasiados intentos fallidos o bloqueo administrativo.
        if (!AccountStatus.ACTIVE.equals(user.getAccountStatus())) {
            throw new LoginException("La cuenta no está activa. Verifica tu correo o contacta al administrador");
        }

        // 5. Comparar la contraseña con el hash BCrypt guardado en BD
        // passwordEncoder.matches("1234", "$2a$10$...") → true o false
        if (!passwordEncoder.matches(dto.getPassword(), credential.getPassword())) {

            // Incrementar intentos fallidos
            int failedAttempts = credential.getFailedAttempts() + 1;
            credential.setFailedAttempts(failedAttempts);
            if (failedAttempts >= AppConstants.MAX_LOGIN_ATTEMPTS) {
                credential.setLockedUntil(OffsetDateTime.now().plusMinutes(AppConstants.LOGIN_LOCK_MINUTES));
            }
            credentialRepository.save(credential);
            notifyFailedLogin(user, failedAttempts);

            throw new LoginException("Usuario o contraseña incorrectos");
        }

        boolean privacyAlreadyAccepted = acceptanceTermsRepository.existsByUserAndAcceptedTrueAndNoticeVersion(
                user, PrivacyNoticePolicy.CURRENT_VERSION);
        if (!privacyAlreadyAccepted) {
            AcceptanceTerms acceptance = acceptanceTermsRepository
                    .findByUserAndNoticeVersion(user, PrivacyNoticePolicy.CURRENT_VERSION)
                    .orElseGet(AcceptanceTerms::new);
            acceptance.setUser(user);
            acceptance.setNoticeVersion(PrivacyNoticePolicy.CURRENT_VERSION);
            acceptance.setAccepted(dto.isPrivacyAccepted());
            acceptanceTermsRepository.save(acceptance);

            if (!dto.isPrivacyAccepted()) {
                throw new LoginException("No aceptaste el aviso de privacidad. El acceso requiere autorización para tratar los datos básicos.");
            }
        }

        // 6. Resetear intentos fallidos — login exitoso
        credential.setFailedAttempts(0);
        credentialRepository.save(credential);

        // 7. Buscar el rol del usuario en user_role
        // Si no tiene rol asignado → el administrador no lo ha configurado aún
        UserRole userRole = userRoleRepository.findByUserId(user.getIdUser())
                .orElseThrow(() -> new LoginException("Sin permisos asignados, contacta al administrador"));

        // 8. Obtener el nombre del rol como String
        // Ejemplo: "INSTRUCTOR"
        String roleName = userRole.getRole().getNameRole().name();

        // 9. Buscar todos los permisos asociados a ese rol en role_permission
        // Ejemplo: ["VIEW_OWN_PROFILE", "VIEW_ATTENDANCE", "VIEW_FICHA_ATTENDANCE"]
        List<RolePermission> rolePermissions = rolePermissionRepository
                .findByRole_IdRole(userRole.getRole().getIdRole());

        // 10. Extraer solo los nombres de los permisos como lista de Strings
        List<String> permissions = rolePermissions.stream()
                .map(rp -> rp.getPermission().getNamePermission())
                .collect(Collectors.toList());

        boolean firstAccessPasswordPrompt = !user.isInitialPasswordChanged();
        if (firstAccessPasswordPrompt && !user.isInitialPasswordPromptSeen()) {
            user.setInitialPasswordPromptSeen(true);
        }

        // 11. Generar el JWT con userId, email, rol y permisos
        String token = jwtService.generateToken(user, roleName, permissions);

        // La emisión del JWT registra y vincula exactamente una sesión (sid).

        // 12. Devolver la respuesta con el token
        return LoginResponseDTO.success(token, roleName, permissions, user.getIdUser(), firstAccessPasswordPrompt,
                !user.isInitialPasswordChanged());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyPassword(UUID userId, String password) {
        return credentialRepository.findByUser_IdUser(userId)
                .map(credential -> passwordEncoder.matches(password, credential.getPassword()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAcceptedPrivacy(String documentNumber) {
        String normalizedDocument = documentNumber == null ? "" : documentNumber.trim();
        if (normalizedDocument.isBlank()) {
            return false;
        }

        return credentialRepository.findByUser_DocumentNumber(normalizedDocument)
                .map(Credential::getUser)
                .map(user -> acceptanceTermsRepository.existsByUserAndAcceptedTrueAndNoticeVersion(
                        user, PrivacyNoticePolicy.CURRENT_VERSION))
                .orElse(false);
    }

    private void notifyFailedLogin(User user, int failedAttempts) {
        try {
            if (failedAttempts >= AppConstants.MAX_LOGIN_ATTEMPTS) {
                notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                        "security_account_locked",
                        "Cuenta bloqueada por intentos fallidos",
                        "La cuenta con documento " + user.getDocumentNumber()
                                + " quedo bloqueada temporalmente tras superar el limite de intentos fallidos.",
                        null,
                        user.getIdUser(),
                        "user",
                        null,
                        "{\"accountDocument\":\"" + user.getDocumentNumber() + "\",\"failedCount\":" + failedAttempts
                                + ",\"lockMinutes\":" + AppConstants.LOGIN_LOCK_MINUTES + "}"));
                return;
            }

            if (failedAttempts >= 2) {
                notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                        "security_multiple_failures",
                        "Multiples intentos fallidos de inicio de sesion",
                        "La cuenta con documento " + user.getDocumentNumber()
                                + " acumulo " + failedAttempts + " intentos fallidos consecutivos.",
                        null,
                        user.getIdUser(),
                        "user",
                        null,
                        "{\"accountDocument\":\"" + user.getDocumentNumber() + "\",\"failedCount\":" + failedAttempts + "}"));
            }
        } catch (RuntimeException ignored) {
            // El login no debe fallar por un problema registrando la notificacion.
        }
    }

}
