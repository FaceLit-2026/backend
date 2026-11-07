package com.FaceLit.backend.auth.service.serviceImpl.roleandpermission;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.FaceLit.backend.auth.dto.response.security.UserListResponseDTO;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.dto.request.roleandpermission.AssignRoleRequestDTO;
import com.FaceLit.backend.auth.dto.response.roleandpermission.AssignRoleResponseDTO;
import com.FaceLit.backend.auth.exception.LoginException;
import com.FaceLit.backend.auth.model.roleandpermission.Role;
import com.FaceLit.backend.auth.model.roleandpermission.UserRole;
import com.FaceLit.backend.auth.repository.roleandpermission.RoleRepository;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.auth.repository.security.CredentialRepository;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.auth.service.roleandpermission.AdminRoleService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminRoleServiceImpl implements AdminRoleService {
    private final com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService sessions;

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final CredentialRepository credentialRepository;
    private final com.FaceLit.backend.notification.service.NotificationService notifications;
    private final com.FaceLit.backend.shared.service.UserDisplayNameService names;

    public AdminRoleServiceImpl(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            CredentialRepository credentialRepository,
            com.FaceLit.backend.notification.service.NotificationService notifications,
            com.FaceLit.backend.shared.service.UserDisplayNameService names, com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService sessions) {

        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.credentialRepository = credentialRepository;
        this.notifications = notifications; this.names = names;
        this.sessions = sessions;
    }

    @Override
    public List<UserListResponseDTO> getAllUsers() {

        // 1. Traer todos los usuarios de BD
        List<User> users = userRepository.findAll();

        // 2. Convertir cada User a UserListResponseDTO
        return users.stream().map(user -> {

            // 3. Obtener el email desde Credential
            String email = credentialRepository.findByUser(user)
                    .map(c -> c.getEmail())
                    .orElse("Sin correo");

            // 4. Obtener el rol actual desde UserRole
            String currentRole = userRoleRepository.findByUserId(user.getIdUser())
                    .map(ur -> ur.getRole().getNameRole().name())
                    .orElse("Sin rol");

            return new UserListResponseDTO(
                    user.getIdUser(),
                    user.getFirstName(),
                    user.getLastName(),
                    email,
                    user.getDocumentNumber(),
                    currentRole
            );
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AssignRoleResponseDTO assignRole(UUID userId, AssignRoleRequestDTO dto, UUID actorId) {

        // 1. Verificar que el usuario existe
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new LoginException("Usuario no encontrado"));

        credentialRepository.lockByUserId(userId);
        // 2. Buscar el rol que se quiere asignar
        Role newRole = roleRepository.findByNameRole(dto.getRole())
                .orElseThrow(() -> new LoginException("Rol no encontrado"));

        // 3. Buscar si ya tiene un rol asignado
        // Si ya tiene uno → actualizarlo
        // Si no tiene → crear uno nuevo
        UserRole userRole = userRoleRepository.findByUserId(userId)
                .orElse(new UserRole());

        String previous = userRole.getRole() == null ? "NONE" : userRole.getRole().getNameRole().name();
        String next = newRole.getNameRole().name();
        if (previous.equals(next)) return AssignRoleResponseDTO.success(userId, next);

        userRole.setUser(user);
        userRole.setRole(newRole);
        userRole.setAssignedAt(OffsetDateTime.now());
        userRoleRepository.save(userRole);

        sessions.revokeAll(userId);

        // Initial assignment belongs to account creation, not to a change of role.
        if ("NONE".equals(previous)) return AssignRoleResponseDTO.success(userId, next);

        String actorName = names.fullName(actorId);
        if (actorName == null || actorName.isBlank()) actorName = "Sistema";
        var metadata = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        metadata.put("previousRole", previous); metadata.put("newRole", next);
        metadata.put("changedByName", actorName);
        String recipientName = (user.getFirstName() == null ? "" : user.getFirstName()) + " " + (user.getLastName() == null ? "" : user.getLastName());
        metadata.put("recipientName", recipientName.trim()); metadata.put("recipientContext", "account_owner");
        metadata.put("occurredAt", userRole.getAssignedAt().toString());
        if (actorId != null) metadata.put("changedBy", actorId.toString());
        notifications.createForRecipient(new com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO(
                userId, "user_role_changed", "Rol de usuario modificado",
                "Destinatario: " + recipientName.trim() + ". Tu rol cambió de " + roleLabel(previous) + " a " + roleLabel(next)
                        + ". Modificado por: " + actorName + ". Fecha: " + userRole.getAssignedAt().atZoneSameInstant(java.time.ZoneId.of("America/Bogota"))
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        + " (hora de Colombia). Recibes este aviso porque cambió el acceso de tu cuenta. Cierra sesión y vuelve a ingresar para cargar el nuevo rol. Si no esperabas el cambio, contacta al coordinador.",
                userId, "user_app", null, metadata.toString()));

        // 4. Responder con el userId, el nuevo rol y mensaje de éxito
        return AssignRoleResponseDTO.success(userId, newRole.getNameRole().name());
    }

    private String roleLabel(String role) {
        return switch (role) { case "APPRENTICE" -> "Aprendiz"; case "INSTRUCTOR" -> "Instructor"; case "COORDINATOR" -> "Coordinador"; default -> "Sin rol"; };
    }
}
