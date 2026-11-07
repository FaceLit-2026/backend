package com.FaceLit.backend.academic.service.notification;

import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Notification composition for the corresponding application flow. */
@Service
public class InstructorNotificationService {
    private final NotificationService notificationService;

    public InstructorNotificationService(
            NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void notifyInstructor(Instructor instructor, String type, String title, String message) {
        try {
            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    instructor.getUser().getIdUser(),
                    type,
                    title,
                    message,
                    instructor.getIdInstructor(),
                    "instructor",
                    null,
                    "{\"entityType\":\"instructor\",\"entityId\":\"" + instructor.getIdInstructor() + "\"}"));
        } catch (RuntimeException ignored) {
            // La actualizacion academica no debe fallar por notificaciones.
        }
    }

    public String[] values(String oldValue, String newValue) {
        return new String[] { oldValue == null ? "" : oldValue, newValue == null ? "" : newValue };
    }

    private String describeChanges(Map<String, String[]> changes) {
        Map<String, String[]> changed = new LinkedHashMap<>();
        changes.forEach((field, values) -> {
            if (!values[0].equals(values[1])) {
                changed.put(field, values);
            }
        });
        if (changed.isEmpty()) {
            return "No se detectaron diferencias visibles.";
        }
        return changed.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + emptyAsNone(entry.getValue()[0]) + " -> " + emptyAsNone(entry.getValue()[1]))
                .collect(Collectors.joining("; "));
    }

    public String listOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "sin programas" : String.join(", ", values);
    }

    public String emptyAsNone(String value) {
        return value == null || value.isBlank() ? "sin dato" : value;
    }

    private void notifyDeleteBlocked(Instructor instructor) {
        try {
            notificationService.createIndependentCoordinatorEvent(new MonolithEventRequestDTO(
                    "academic_delete_blocked",
                    "Intento de eliminacion bloqueado",
                    "No fue posible eliminar permanentemente al instructor "
                            + instructor.getUser().getFirstName() + " " + instructor.getUser().getLastName()
                            + " porque tiene programas asociados.",
                    null,
                    instructor.getIdInstructor(),
                    "instructor",
                    null,
                    "{\"entityType\":\"instructor\",\"entityId\":\"" + instructor.getIdInstructor() + "\"}"));
        } catch (RuntimeException ignored) {
            // La eliminacion academica no debe fallar por notificaciones.
        }
    }

    public void notifyInstructorDeactivated(Instructor instructor) {
        User user = instructor.getUser();
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        try {
            notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                    "academic_entity_deactivated",
                    "Instructor desactivado",
                    "Se desactivo el instructor " + emptyAsNone(fullName)
                            + " con documento " + emptyAsNone(user.getDocumentNumber()) + ".",
                    null,
                    instructor.getIdInstructor(),
                    "instructor",
                    null,
                    "{\"entityType\":\"instructor\",\"entityId\":\"" + instructor.getIdInstructor()
                            + "\",\"instructorName\":\"" + safe(fullName)
                            + "\",\"document\":\"" + safe(user.getDocumentNumber()) + "\"}"));
        } catch (RuntimeException ignored) {
            // La desactivacion academica no debe fallar por notificaciones.
        }
    }

    public void notifyCoordinatorInstructorEvent(
            String type,
            String title,
            String message,
            Instructor instructor,
            String action) {
        User user = instructor.getUser();
        try {
            notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                    type,
                    title,
                    message,
                    null,
                    instructor.getIdInstructor(),
                    "instructor",
                    null,
                    "{\"entityType\":\"instructor\",\"entityId\":\"" + instructor.getIdInstructor()
                            + "\",\"action\":\"" + safe(action)
                            + "\",\"instructorName\":\"" + safe(instructorName(instructor))
                            + "\",\"document\":\"" + safe(user.getDocumentNumber()) + "\"}"));
        } catch (RuntimeException ignored) {
            // La actualizacion academica no debe fallar por notificaciones.
        }
    }

    public String instructorName(Instructor instructor) {
        User user = instructor.getUser();
        return emptyAsNone(((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim());
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
