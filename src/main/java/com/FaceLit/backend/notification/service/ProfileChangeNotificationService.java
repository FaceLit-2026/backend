package com.FaceLit.backend.notification.service;

import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.shared.service.UserDisplayNameService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Only allowlisted profile fields enter notification metadata; credentials are never included. */
@Service
public class ProfileChangeNotificationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProfileChangeNotificationService.class);
    private static final Map<String, String> LABELS = Map.of("nombre", "Nombre", "apellido", "Apellido",
            "documento", "Documento", "correo", "Correo electronico", "correoPendiente", "Correo pendiente de verificacion",
            "estado", "Estado de cuenta", "rol", "Rol", "tipoInstructor", "Tipo de instructor");
    private final NotificationService notifications;
    private final UserRoleRepository roles;
    private final UserDisplayNameService names;
    private final ObjectMapper mapper;

    public ProfileChangeNotificationService(NotificationService notifications, UserRoleRepository roles,
            UserDisplayNameService names, ObjectMapper mapper) {
        this.notifications = notifications; this.roles = roles; this.names = names; this.mapper = mapper;
    }

    public static String roleLabel(String value) {
        if (value == null) return "Sin rol";
        return switch (value) {
            case "APPRENTICE" -> "Aprendiz";
            case "INSTRUCTOR" -> "Instructor";
            case "COORDINATOR" -> "Coordinador";
            default -> "Sin rol";
        };
    }

    public static String[] pair(String before, String after) { return new String[]{before, after}; }

    public void notifyChanges(User user, String type, String entityType, Map<String, String[]> fields) {
        var changes = mapper.createArrayNode();
        var lines = new ArrayList<String>();
        fields.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (!LABELS.containsKey(entry.getKey()) || entry.getValue() == null || entry.getValue().length != 2) return;
            String before = Objects.toString(entry.getValue()[0], "");
            String after = Objects.toString(entry.getValue()[1], "");
            if (before.equals(after)) return;
            var change = changes.addObject(); change.put("field", entry.getKey());
            change.put("before", before); change.put("after", after);
            lines.add(LABELS.get(entry.getKey()) + ": " + display(entry.getKey(), before) + " → " + display(entry.getKey(), after));
        });
        if (changes.isEmpty()) return;
        UUID actor = actorId();
        String actorName = actor == null ? "Sistema" : names.fullName(actor);
        if (actorName == null || actorName.isBlank()) actorName = "Usuario autorizado";
        String affectedName = (Objects.toString(user.getFirstName(), "") + " " + Objects.toString(user.getLastName(), "")).trim();
        var metadata = mapper.createObjectNode();
        metadata.put("entityType", entityType); metadata.put("entityId", user.getIdUser().toString());
        metadata.put("action", fields.containsKey("correoPendiente") ? "email_change_requested" : "profile_updated");
        metadata.set("profileChanges", changes); metadata.put("changedByName", actorName);
        metadata.put("affectedUserName", affectedName);
        if (actor != null) metadata.put("changedBy", actor.toString());
        OffsetDateTime occurredAt = OffsetDateTime.now(); metadata.put("occurredAt", occurredAt.toString());
        String message = "Datos de " + affectedName + ". Cambios realizados:\n" + String.join("\n", lines)
                + "\nModificado por: " + actorName + ". Fecha: " + occurredAt.atZoneSameInstant(ZoneId.of("America/Bogota"))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " (hora de Colombia).";
        if (fields.containsKey("correoPendiente")) message += " El correo y la contrasena actuales se conservan hasta completar la verificacion del nuevo correo.";
        String title = fields.containsKey("correoPendiente") ? "Cambio de correo pendiente" : "Datos del usuario actualizados";
        createNotice(new CreateNotificationRequestDTO(user.getIdUser(), type, title, message,
                user.getIdUser(), "user_app", null, metadata.toString()));
        // Do not broadcast personal snapshots to all coordinators or accept the actor from client input.
        if (actor != null && !actor.equals(user.getIdUser()) && roles.findByUserId(actor)
                .map(r -> r.getRole().getNameRole() == RoleName.COORDINATOR).orElse(false)) {
            createNotice(new CreateNotificationRequestDTO(actor, type, title, message,
                    user.getIdUser(), "user_app", null, metadata.toString()));
        }
    }

    private void createNotice(CreateNotificationRequestDTO request) {
        try { notifications.createForRecipient(request); }
        catch (RuntimeException ex) {
            // Preserve the existing behavior: informational notices must not reject a profile update.
            log.warn("No se pudo registrar el aviso de perfil para {} ({})", request.recipientUserId(), ex.getClass().getSimpleName());
        }
    }

    private UUID actorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth.getPrincipal() instanceof UUID id) return id;
        try { return UUID.fromString(Objects.toString(auth.getPrincipal(), "")); }
        catch (IllegalArgumentException ex) { return null; }
    }
    private static String display(String field, String value) {
        if (value.isBlank()) return "Sin dato";
        if (field.equals("rol")) return roleLabel(value);
        if (field.equals("tipoInstructor")) return value.equals("ESPECIFICO") ? "Específico" : "Transversal";
        if (field.equals("estado")) return switch (value) { case "ACTIVE" -> "Activa"; case "INACTIVE" -> "Inactiva"; default -> "Pendiente"; };
        return value;
    }
}
