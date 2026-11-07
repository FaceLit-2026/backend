package com.FaceLit.backend.facial.service;

import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.facial.model.FacialEvent;
import com.FaceLit.backend.facial.model.enums.AttendanceStatus;
import com.FaceLit.backend.facial.model.enums.FacialEventType;
import com.FaceLit.backend.facial.repository.AttendanceReadRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Attendance notification composition; preserves recipients, texts and metadata. */
@Service
public class AttendanceNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttendanceNotificationService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ZoneId SESSION_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter SESSION_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SESSION_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final NotificationService notificationService;
    private final AttendanceReadRepository attendanceReadRepository;

    public AttendanceNotificationService(NotificationService notificationService, AttendanceReadRepository attendanceReadRepository) {
        this.notificationService = notificationService;
        this.attendanceReadRepository = attendanceReadRepository;
    }

    public void notifyExcuseRemoved(FacialEvent event, UUID actor) {
        try {
            String actorName = attendanceReadRepository.actorName(actor);
            var meta = (com.fasterxml.jackson.databind.node.ObjectNode) OBJECT_MAPPER.readTree(metadataFor(event, "corrected"));
            meta.put("previousStatus", "ABSENT"); meta.put("newStatus", "ABSENT");
            meta.put("previousExcuse", true); meta.put("newExcuse", false);
            meta.put("previousDatetime", event.getEventDatetime().toString()); meta.put("newDatetime", event.getEventDatetime().toString());
            meta.put("changedByName", actorName); meta.put("changedBy", actor.toString());
            meta.put("correctionReasonCode", "excuse_removed");
            meta.put("correctedAt", OffsetDateTime.now().toString());
            String message = "Se retiró la excusa: la inasistencia pasó de justificada a sin excusa. Modificado por: " + actorName + ".\n" + attendanceContext(event);
            for (UUID recipient : new java.util.LinkedHashSet<>(List.of(event.getApprentice().getUser().getIdUser(), responsibleInstructor(event.getRecordEnvironment()).getUser().getIdUser()))) {
                boolean learner = recipient.equals(event.getApprentice().getUser().getIdUser());
                var user = learner ? event.getApprentice().getUser() : responsibleInstructor(event.getRecordEnvironment()).getUser();
                meta.put("recipientName", fullName(user.getFirstName(), user.getLastName()));
                meta.put("recipientContext", learner ? "learner" : "responsible_instructor");
                notificationService.createForRecipient(new CreateNotificationRequestDTO(recipient, "attendance_corrected", "Asistencia corregida",
                        "Destinatario: " + fullName(user.getFirstName(), user.getLastName()) + ".\n" + message
                                + (learner ? "\nRecibes este aviso porque se modificó tu asistencia." : "\nRecibes este aviso porque eres el instructor responsable seleccionado para esta sesión.")
                                + " Revisa el registro actualizado. Si no estás de acuerdo, contacta al coordinador.",
                        event.getRecordEnvironment().getIdRecordEnvironment(), "record_environment", event.getIdFacialEvent(), meta.toString()));
            }
        } catch (JsonProcessingException error) { throw new IllegalStateException(error); }
    }

    public void notifyAttendanceEvent(FacialEvent event) {
        if (event.getEventType() == FacialEventType.ENTRY && event.getAttendanceStatus() == AttendanceStatus.LATE) {
            createNotification(
                    event.getApprentice().getUser().getIdUser(),
                    "attendance_late",
                    "Retraso registrado",
                    "Tu asistencia fue registrada como tardia para la sesion de la ficha "
                            + event.getRecordEnvironment().getChip().getChipCode() + ".\n" + attendanceContext(event),
                    event,
                    metadataFor(event, "late"));
            return;
        }

        if (event.getEventType() == FacialEventType.EXIT
                && event.getRecordEnvironment().getExitTime() != null
                && event.getEventDatetime().isBefore(event.getRecordEnvironment().getExitTime())) {
            Instructor instructor = responsibleInstructor(event.getRecordEnvironment());
            createNotification(
                    instructor.getUser().getIdUser(),
                    "attendance_early_exit",
                    "Salida anticipada",
                    fullName(event.getApprentice().getUser().getFirstName(), event.getApprentice().getUser().getLastName())
                            + " registro salida antes de la hora configurada para la ficha "
                            + event.getRecordEnvironment().getChip().getChipCode() + ".\n" + attendanceContext(event)
                            + "\nHora de salida registrada: " + event.getEventDatetime().atZoneSameInstant(SESSION_ZONE).format(SESSION_TIME),
                    event,
                    metadataFor(event, "early_exit"));
        }
    }

    public void notifyExcuseOrAbsence(FacialEvent event, boolean newAbsentEvent, Boolean previousExcuse) {
        boolean excuseRegistered = Boolean.TRUE.equals(event.getExcuse()) && !Boolean.TRUE.equals(previousExcuse);
        if (excuseRegistered) {
            String message = "Se registró una excusa para la inasistencia.\n" + attendanceContext(event);
            createNotification(
                    event.getApprentice().getUser().getIdUser(),
                    "attendance_excuse_registered",
                    "Excusa registrada",
                    message,
                    event,
                    metadataFor(event, "excuse_registered"));
            createNotification(
                    responsibleInstructor(event.getRecordEnvironment()).getUser().getIdUser(),
                    "attendance_excuse_registered",
                    "Excusa registrada",
                    message,
                    event,
                    metadataFor(event, "excuse_registered"));
            return;
        }

        if (newAbsentEvent && event.getAttendanceStatus() == AttendanceStatus.ABSENT
                && !event.getRecordEnvironment().getShutdownTime().isAfter(OffsetDateTime.now())) {
            createNotification(
                    event.getApprentice().getUser().getIdUser(),
                    "attendance_absent",
                    "Inasistencia registrada",
                    "Se registró una inasistencia.\n" + attendanceContext(event),
                    event,
                    metadataFor(event, "absent"));
        }
    }

    public String attendanceContext(FacialEvent event) {
        RecordEnvironment record = event.getRecordEnvironment();
        var learner = event.getApprentice().getUser();
        var instructor = responsibleInstructor(record).getUser();
        var sessionStart = record.getEntryTime().atZoneSameInstant(SESSION_ZONE);
        return "Aprendiz: " + fullName(learner.getFirstName(), learner.getLastName())
                + "\nFecha de la sesión: " + sessionStart.format(SESSION_DATE)
                + "\nHora de inicio: " + sessionStart.format(SESSION_TIME) + " (hora de Colombia)"
                + "\nFicha: " + record.getChip().getChipCode()
                + "\nInstructor responsable de la sesión: "
                + fullName(instructor.getFirstName(), instructor.getLastName()) + ".";
    }

    private void createNotification(
            UUID recipientUserId,
            String type,
            String title,
            String message,
            FacialEvent event,
            String metadataJson) {
        try {
            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    recipientUserId,
                    type,
                    title,
                    message,
                    event.getRecordEnvironment().getIdRecordEnvironment(),
                    "record_environment",
                    event.getIdFacialEvent(),
                    metadataJson));
        } catch (RuntimeException ex) {
            LOGGER.warn("No fue posible crear la notificacion {} para el usuario {}.", type, recipientUserId, ex);
        }
    }

    private Instructor responsibleInstructor(RecordEnvironment record) {
        return record.getInstructorScheduled();
    }

    public String metadataFor(FacialEvent event, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reason", reason);
        metadata.put("idRecordEnvironment", event.getRecordEnvironment().getIdRecordEnvironment());
        metadata.put("idFacialEvent", event.getIdFacialEvent());
        metadata.put("idApprentice", event.getApprentice().getIdApprentice());
        metadata.put("chipCode", event.getRecordEnvironment().getChip().getChipCode());
        var learner = event.getApprentice().getUser();
        Instructor instructor = responsibleInstructor(event.getRecordEnvironment());
        var sessionStart = event.getRecordEnvironment().getEntryTime().atZoneSameInstant(SESSION_ZONE);
        metadata.put("learnerName", fullName(learner.getFirstName(), learner.getLastName()));
        metadata.put("sessionDate", sessionStart.toLocalDate().toString());
        metadata.put("sessionStart", sessionStart.toOffsetDateTime().toString());
        metadata.put("sessionTimezone", SESSION_ZONE.getId());
        metadata.put("idInstructor", instructor.getIdInstructor());
        metadata.put("instructorName", fullName(instructor.getUser().getFirstName(), instructor.getUser().getLastName()));
        metadata.put("attendanceStatus", event.getAttendanceStatus().name());
        metadata.put("eventType", event.getEventType().name());
        metadata.put("eventDatetime", event.getEventDatetime().toString());
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            LOGGER.warn("No fue posible serializar metadata de notificacion para evento facial {}.",
                    event.getIdFacialEvent(),
                    ex);
            return null;
        }
    }

    private String fullName(String firstName, String lastName) {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

}
