package com.FaceLit.backend.environment.service;

import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.academic.model.academic.UserChip;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.InstructorRepository;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Notification composition for the corresponding application flow. */
@Service
public class EnvironmentSessionNotifier {
    private final InstructorRepository instructorRepository;
    private final UserChipRepository userChipRepository;
    private final NotificationService notificationService;

    public EnvironmentSessionNotifier(
            InstructorRepository instructorRepository,
            UserChipRepository userChipRepository,
            NotificationService notificationService) {
        this.instructorRepository = instructorRepository;
        this.userChipRepository = userChipRepository;
        this.notificationService = notificationService;
    }

    public String fullName(Instructor instructor) {
        return "%s %s".formatted(instructor.getUser().getFirstName(), instructor.getUser().getLastName()).trim();
    }

    public void notifySessionSubstitution(RecordEnvironment record, UUID authenticatedUserId) {
        Instructor configuredBy = instructorRepository.findByUser_IdUser(authenticatedUserId).orElse(null);
        Instructor scheduledInstructor = record.getInstructorScheduled();
        if (configuredBy == null || scheduledInstructor == null
                || configuredBy.getIdInstructor().equals(scheduledInstructor.getIdInstructor())) {
            return;
        }

        String metadata = sessionMetadata(record, "configured", configuredBy);

        try {
            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    scheduledInstructor.getUser().getIdUser(),
                    "facial_session_substitution",
                    "Sesion de reconocimiento asignada",
                    "Se configuró una sesión de asistencia y quedaste como instructor responsable.\n" + sessionContext(record, configuredBy),
                    record.getIdRecordEnvironment(),
                    "record_environment",
                    null,
                    metadata));

            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    configuredBy.getUser().getIdUser(),
                    "facial_session_substitution",
                    "Sesion de reconocimiento configurada",
                    "Configuraste una sesión de asistencia para otro instructor responsable.\n" + sessionContext(record, configuredBy),
                    record.getIdRecordEnvironment(),
                    "record_environment",
                    null,
                    metadata));
        } catch (RuntimeException ignored) {
            // La configuracion de la sesion no debe fallar por notificaciones informativas.
        }
    }

    public void notifySessionAssigned(RecordEnvironment record) {
        Instructor responsible = record.getInstructorScheduled();
        try {
            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    responsible.getUser().getIdUser(),
                    "session_assigned",
                    "Nueva sesion asignada",
                    "Configuraste una sesión de asistencia y eres su instructor responsable.\n" + sessionContext(record, responsible),
                    record.getIdRecordEnvironment(),
                    "record_environment",
                    null,
                    sessionMetadata(record, "assigned", responsible)));
        } catch (RuntimeException ignored) {
            // La configuracion de la sesion no debe fallar por notificaciones informativas.
        }
    }

    public void notifySessionUpdated(RecordEnvironment record, SessionSnapshot previous, UUID authenticatedUserId) {
        List<String> changes = previous.describeChanges(record);
        if (changes.isEmpty()) {
            return;
        }

        Set<UUID> recipients = new HashSet<>();
        Instructor configuredBy = instructorRepository.findByUser_IdUser(authenticatedUserId).orElseThrow();
        recipients.add(record.getInstructorScheduled().getUser().getIdUser());
        recipients.add(configuredBy.getUser().getIdUser());
        recipients.add(responsibleInstructor(record).getUser().getIdUser());
        if (previous.responsibleUserId() != null) {
            recipients.add(previous.responsibleUserId());
        }
        userChipRepository.findByChip_IdChipAndState(record.getChip().getIdChip(), AcademicState.ACTIVE)
                .stream()
                .map(UserChip::getApprentice)
                .map(apprentice -> apprentice.getUser().getIdUser())
                .forEach(recipients::add);

        String message = "La sesion de la ficha " + record.getChip().getChipCode()
                + " fue actualizada. Cambios: " + String.join("; ", changes) + ".\n" + sessionContext(record, configuredBy);
        String metadata = sessionMetadata(record, "updated", configuredBy);
        for (UUID recipientId : recipients) {
            try {
                notificationService.createForRecipient(new CreateNotificationRequestDTO(
                        recipientId,
                        "session_updated",
                        "Sesion actualizada",
                        message,
                        record.getIdRecordEnvironment(),
                        "record_environment",
                        null,
                        metadata));
            } catch (RuntimeException ignored) {
                // La actualizacion de la sesion no debe fallar por notificaciones informativas.
            }
        }
    }

    private Instructor responsibleInstructor(RecordEnvironment record) {
        return record.getInstructorInCharge() != null ? record.getInstructorInCharge() : record.getInstructorScheduled();
    }

    private String sessionMetadata(RecordEnvironment record, String action, Instructor configuredBy) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("entityType", "record_environment");
        metadata.put("entityId", record.getIdRecordEnvironment());
        metadata.put("action", action);
        metadata.put("environment", record.getEnvironment().getEnvironmentName());
        metadata.put("chip", record.getChip().getChipCode());
        metadata.put("instructorName", fullName(record.getInstructorScheduled()));
        metadata.put("responsibleInstructorUserId", record.getInstructorScheduled().getUser().getIdUser());
        metadata.put("configuredByName", fullName(configuredBy));
        metadata.put("configuredByUserId", configuredBy.getUser().getIdUser());
        metadata.put("entryTime", record.getEntryTime().toString());
        metadata.put("exitTime", record.getExitTime().toString());
        metadata.put("shutdownTime", record.getShutdownTime().toString());
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("No se pudo serializar la sesión", ex); }
    }

    private String sessionContext(RecordEnvironment record, Instructor configuredBy) {
        var zone = java.time.ZoneId.of("America/Bogota");
        var format = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return "Instructor que configuró la sesión (usuario autenticado): " + fullName(configuredBy)
                + "\nInstructor responsable seleccionado: " + fullName(record.getInstructorScheduled())
                + "\nFicha: " + record.getChip().getChipCode()
                + "\nAmbiente: " + record.getEnvironment().getEnvironmentName()
                + "\nInicio: " + record.getEntryTime().atZoneSameInstant(zone).format(format)
                + "\nSalida: " + record.getExitTime().atZoneSameInstant(zone).format(format)
                + "\nApagado: " + record.getShutdownTime().atZoneSameInstant(zone).format(format) + " (hora de Colombia).";
    }
    public record SessionSnapshot(
            UUID environmentId,
            String environmentName,
            UUID chipId,
            String chipCode,
            UUID responsibleInstructorId,
            UUID responsibleUserId,
            java.time.OffsetDateTime entryTime,
            java.time.OffsetDateTime exitTime,
            java.time.OffsetDateTime shutdownTime) {

        public static SessionSnapshot from(RecordEnvironment record) {
            Instructor responsible = record.getInstructorScheduled();
            return new SessionSnapshot(
                    record.getEnvironment().getIdEnvironment(),
                    record.getEnvironment().getEnvironmentName(),
                    record.getChip().getIdChip(),
                    record.getChip().getChipCode(),
                    responsible.getIdInstructor(),
                    responsible.getUser().getIdUser(),
                    record.getEntryTime(),
                    record.getExitTime(),
                    record.getShutdownTime());
        }

        public List<String> describeChanges(RecordEnvironment record) {
            java.util.ArrayList<String> changes = new java.util.ArrayList<>();
            add(changes, "ambiente", environmentId, record.getEnvironment().getIdEnvironment(), environmentName,
                    record.getEnvironment().getEnvironmentName());
            add(changes, "ficha", chipId, record.getChip().getIdChip(), chipCode, record.getChip().getChipCode());
            Instructor responsible = record.getInstructorScheduled();
            add(changes, "responsable", responsibleInstructorId, responsible.getIdInstructor(), "anterior",
                    instructorName(responsible));
            add(changes, "entrada", entryTime, record.getEntryTime(), entryTime, record.getEntryTime());
            add(changes, "salida", exitTime, record.getExitTime(), exitTime, record.getExitTime());
            add(changes, "apagado", shutdownTime, record.getShutdownTime(), shutdownTime, record.getShutdownTime());
            return changes;
        }

        private void add(List<String> changes, String field, Object oldValue, Object newValue, Object oldLabel, Object newLabel) {
            if (!java.util.Objects.equals(oldValue, newValue)) {
                changes.add(field + ": " + oldLabel + " -> " + newLabel);
            }
        }

        private static String instructorName(Instructor instructor) {
            return "%s %s".formatted(instructor.getUser().getFirstName(), instructor.getUser().getLastName()).trim();
        }
    }
}
