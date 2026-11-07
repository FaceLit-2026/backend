package com.FaceLit.backend.auth.service.notification;

import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;
import org.springframework.stereotype.Service;

/** Notification composition for the corresponding application flow. */
@Service
public class ManagedUserNotificationService {
    private final NotificationService notificationService;

    public ManagedUserNotificationService(
            NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public String emptyAsNone(String value) {
        return value == null || value.isBlank() ? "sin dato" : value;
    }

    public void notifyUser(User user, String type, String title, String message, String metadata) {
        try {
            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    user.getIdUser(),
                    type,
                    title,
                    message,
                    user.getIdUser(),
                    "user",
                    null,
                    metadata));
        } catch (RuntimeException ignored) {
            // La gestion de usuario no debe fallar por una notificacion informativa.
        }
    }

    public void notifyAcademicUserStatusChanged(User user, String roleName, boolean activated) {
        boolean apprentice = "APPRENTICE".equals(roleName);
        boolean instructor = "INSTRUCTOR".equals(roleName);
        if (!apprentice && !instructor) {
            return;
        }

        String entityType = apprentice ? "learner" : "instructor";
        String displayRole = apprentice ? "aprendiz" : "instructor";
        String action = activated ? "activated" : "deactivated";
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();

        try {
            notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                    activated ? "academic_entity_activated" : "academic_entity_deactivated",
                    activated
                            ? (apprentice ? "Aprendiz activado" : "Instructor activado")
                            : (apprentice ? "Aprendiz desactivado" : "Instructor desactivado"),
                    "Se " + (activated ? "activo" : "desactivo") + " el " + displayRole + " " + emptyAsNone(fullName)
                            + " con documento " + emptyAsNone(user.getDocumentNumber()) + ".",
                    null,
                    user.getIdUser(),
                    entityType,
                    null,
                    "{\"entityType\":\"" + entityType
                            + "\",\"entityId\":\"" + user.getIdUser()
                            + "\",\"action\":\"" + action
                            + "\",\"name\":\"" + safe(fullName)
                            + "\",\"document\":\"" + safe(user.getDocumentNumber()) + "\"}"));
        } catch (RuntimeException ignored) {
            // La gestion de usuario no debe fallar por una notificacion informativa.
        }
    }

    public void notifyAccountStatusChanged(User user, String statusLabel) {
        try {
            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    user.getIdUser(),
                    "account_status_changed",
                    "Aviso sobre el estado de tu cuenta",
                    "Tu cuenta de FaceLit fue " + statusLabel
                            + ". Si necesitas aclarar esta restriccion, contacta a coordinacion.",
                    user.getIdUser(),
                    "user",
                    null,
                    "{\"entityType\":\"user\",\"entityId\":\"" + user.getIdUser()
                            + "\",\"status\":\"" + safe(statusLabel) + "\"}"));
        } catch (RuntimeException ignored) {
            // La gestion de usuario no debe fallar por una notificacion informativa.
        }
    }

    public void notifyCoordinatorUserEvent(
            String type,
            String title,
            String message,
            User user,
            String action,
            String entityType) {
        try {
            notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                    type,
                    title,
                    message,
                    null,
                    user.getIdUser(),
                    entityType,
                    null,
                    "{\"entityType\":\"" + entityType
                            + "\",\"entityId\":\"" + user.getIdUser()
                            + "\",\"action\":\"" + safe(action)
                            + "\",\"name\":\"" + safe(fullName(user))
                            + "\",\"document\":\"" + safe(user.getDocumentNumber()) + "\"}"));
        } catch (RuntimeException ignored) {
            // La gestion de usuario no debe fallar por una notificacion informativa.
        }
    }

    public String fullName(User user) {
        return emptyAsNone(((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim());
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
