package com.FaceLit.backend.academic.service.notification;

import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.UserChip;
import com.FaceLit.backend.academic.repository.InstructorProgramRepository;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.environment.repository.RecordEnvironmentRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Notification composition for the corresponding application flow. */
@Service
public class LearnerNotificationService {
    private final RecordEnvironmentRepository recordEnvironmentRepository;
    private final InstructorProgramRepository instructorProgramRepository;
    private final NotificationService notificationService;
    private final com.FaceLit.backend.notification.service.ProfileChangeNotificationService profileChanges;

    public LearnerNotificationService(
            RecordEnvironmentRepository recordEnvironmentRepository,
            InstructorProgramRepository instructorProgramRepository,
            NotificationService notificationService,
            com.FaceLit.backend.notification.service.ProfileChangeNotificationService profileChanges) {
        this.recordEnvironmentRepository = recordEnvironmentRepository;
        this.instructorProgramRepository = instructorProgramRepository;
        this.notificationService = notificationService;
        this.profileChanges = profileChanges;
    }

    public void notifyTransfer(User user, Chip previousChip, Chip destination, UserChip newAssignment) {
        String learnerName = (user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName());
        String message = "El aprendiz " + learnerName.trim() + " (" + user.getDocumentNumber()
                + ") fue trasladado de la ficha " + previousChip.getChipCode()
                + " a la ficha " + destination.getChipCode() + ".";

        try {
            notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                    "learner_transferred",
                    "Traslado de aprendiz",
                    message,
                    null,
                    newAssignment.getIdUserChip(),
                    "apprentice_chip",
                    null,
                    transferMetadata(user, previousChip, destination)));

            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    user.getIdUser(),
                    "apprentice_transfer_applied",
                    "Cambio de ficha registrado",
                    "Tu ficha fue cambiada de " + previousChip.getChipCode()
                            + " a " + destination.getChipCode() + ".",
                    newAssignment.getIdUserChip(),
                    "apprentice_chip",
                    null,
                    transferMetadata(user, previousChip, destination)));

            Set<UUID> instructorUserIds = new HashSet<>();
            instructorUserIds.addAll(recordEnvironmentRepository.findDistinctInstructorUserIdsByChip(previousChip.getIdChip()));
            instructorUserIds.addAll(recordEnvironmentRepository.findDistinctInstructorUserIdsByChip(destination.getIdChip()));
            instructorUserIds.addAll(instructorUserIdsByProgram(previousChip));
            instructorUserIds.addAll(instructorUserIdsByProgram(destination));
            for (UUID instructorUserId : instructorUserIds) {
                notificationService.createForRecipient(new CreateNotificationRequestDTO(
                        instructorUserId,
                        "learner_transferred",
                        "Cambio de ficha de aprendiz",
                        message,
                        newAssignment.getIdUserChip(),
                        "apprentice_chip",
                        null,
                        transferMetadata(user, previousChip, destination)));
            }
        } catch (RuntimeException ignored) {
            // El traslado no debe revertirse por una falla al registrar notificaciones.
        }
    }

    private Set<UUID> instructorUserIdsByProgram(Chip chip) {
        UUID programId = chip.getProgram().getIdProgram();
        return instructorProgramRepository.findAll().stream()
                .filter(relation -> relation.getProgram().getIdProgram().equals(programId))
                .map(relation -> relation.getInstructor().getUser().getIdUser())
                .collect(java.util.stream.Collectors.toSet());
    }

    public void notifyApprenticeCreated(User user, Chip chip, UserChip userChip) {
        try {
            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    user.getIdUser(),
                    "user_account_created",
                    "Tu usuario de aprendiz fue creado",
                    "Coordinacion creo tu usuario de aprendiz y te asigno a la ficha "
                            + chip.getChipCode() + ".",
                    userChip.getIdUserChip(),
                    "apprentice_chip",
                    null,
                    "{\"entityType\":\"apprentice_chip\",\"entityId\":\"" + userChip.getIdUserChip()
                            + "\",\"chip\":\"" + safe(chip.getChipCode()) + "\"}"));
            notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                    "academic_entity_created",
                    "Aprendiz creado",
                    "Se creo el aprendiz " + learnerName(user)
                            + " con documento " + emptyAsNone(user.getDocumentNumber())
                            + " y se asigno a la ficha " + chip.getChipCode() + ".",
                    null,
                    userChip.getIdUserChip(),
                    "apprentice_chip",
                    null,
                    "{\"entityType\":\"learner\",\"entityId\":\"" + user.getIdUser()
                            + "\",\"action\":\"created\",\"learnerName\":\"" + safe(learnerName(user))
                            + "\",\"learnerDocument\":\"" + safe(user.getDocumentNumber())
                            + "\",\"fichaNumber\":\"" + safe(chip.getChipCode()) + "\"}"));
        } catch (RuntimeException ignored) {
            // La asignacion academica no debe fallar por una notificacion informativa.
        }
    }

    public void notifyApprenticeProfileUpdated(
            User user,
            UserChip userChip,
            String oldDocument,
            String oldFirstName,
            String oldLastName,
            String oldEmail,
            String newEmail) {
        java.util.List<String> changes = new java.util.ArrayList<>();
        addChange(changes, "documento", oldDocument, user.getDocumentNumber());
        addChange(changes, "nombre", oldFirstName, user.getFirstName());
        addChange(changes, "apellido", oldLastName, user.getLastName());
        addChange(changes, "correo", oldEmail, newEmail);
        if (changes.isEmpty()) {
            return;
        }
        profileChanges.notifyChanges(user, "apprentice_profile_updated", "learner", java.util.Map.of(
                "documento", new String[]{oldDocument, user.getDocumentNumber()},
                "nombre", new String[]{oldFirstName, user.getFirstName()},
                "apellido", new String[]{oldLastName, user.getLastName()},
                "correo", new String[]{oldEmail, newEmail}));
    }

    private void addChange(java.util.List<String> changes, String field, String oldValue, String newValue) {
        String oldSafe = oldValue == null ? "" : oldValue;
        String newSafe = newValue == null ? "" : newValue;
        if (!oldSafe.equals(newSafe)) {
            changes.add(field + ": " + emptyAsNone(oldSafe) + " -> " + emptyAsNone(newSafe));
        }
    }

    private String emptyAsNone(String value) {
        return value == null || value.isBlank() ? "sin dato" : value;
    }

    private String transferMetadata(User user, Chip previousChip, Chip destination) {
        return "{\"learnerName\":\"" + safe(user.getFirstName()) + " " + safe(user.getLastName())
                + "\",\"learnerDocument\":\"" + safe(user.getDocumentNumber())
                + "\",\"fromFichaNumber\":\"" + safe(previousChip.getChipCode())
                + "\",\"toFichaNumber\":\"" + safe(destination.getChipCode()) + "\"}";
    }

    private String learnerName(User user) {
        return emptyAsNone(((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim());
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
