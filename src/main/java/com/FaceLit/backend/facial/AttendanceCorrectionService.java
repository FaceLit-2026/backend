package com.FaceLit.backend.facial;

import java.time.OffsetDateTime;
import java.util.*;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.facial.repository.FacialEventRepository;
import com.FaceLit.backend.facial.model.FacialEvent;
import com.FaceLit.backend.facial.model.enums.*;
import com.FaceLit.backend.facial.dto.response.FacialEventResponseDTO;
import com.FaceLit.backend.facial.service.serviceImpl.FacialEventServiceImpl;
import com.FaceLit.backend.notification.service.NotificationService;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.shared.service.UserDisplayNameService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.validation.constraints.*;

@Service
public class AttendanceCorrectionService {
    public record Correction(@NotNull AttendanceStatus attendanceStatus, OffsetDateTime eventDatetime,
            Boolean excuse, @NotBlank @Size(max = 500) String reason) {}
    private final FacialEventRepository events;
    private final UserRoleRepository roles;
    private final UserDisplayNameService names;
    private final FacialEventServiceImpl attendance;
    private final NotificationService notices;
    public AttendanceCorrectionService(FacialEventRepository events, UserRoleRepository roles, UserDisplayNameService names,
            FacialEventServiceImpl attendance, NotificationService notices) {
        this.events = events; this.roles = roles; this.names = names; this.attendance = attendance; this.notices = notices;
    }

    @Transactional
    public FacialEventResponseDTO correct(UUID id, Correction correction, UUID actor) {
        FacialEvent event = events.lockForNotification(id).filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado"));
        var session = event.getRecordEnvironment();
        RoleName role = roles.findByUserId(actor).map(r -> r.getRole().getNameRole()).orElse(null);
        boolean belongs = session.getInstructorScheduled().getUser().getIdUser().equals(actor)
                || (session.getInstructorInCharge() != null && session.getInstructorInCharge().getUser().getIdUser().equals(actor));
        if (role != RoleName.COORDINATOR && !(role == RoleName.INSTRUCTOR && belongs))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes corregir esta sesión");
        if (session.getDeletedAt() != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Sesión eliminada");
        if (correction.reason() == null || correction.reason().isBlank() || correction.attendanceStatus() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el estado y el motivo de la corrección");
        OffsetDateTime time = correction.attendanceStatus() == AttendanceStatus.ABSENT
                ? session.getEntryTime() : correction.eventDatetime();
        if (time == null || time.isBefore(session.getEntryTime()) || !time.isBefore(session.getShutdownTime()) || time.isAfter(OffsetDateTime.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hora de asistencia inválida");
        if (event.getEventType() == FacialEventType.ENTRY) {
            if (correction.attendanceStatus() != AttendanceStatus.ABSENT) {
                AttendanceStatus expected = time.isAfter(session.getEntryTime().plusMinutes(session.getRegistrationMinutes())) ? AttendanceStatus.LATE : AttendanceStatus.PUNCTUAL;
                if (!time.isBefore(session.getExitTime()) || correction.attendanceStatus() != expected)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado no corresponde a la hora de entrada");
            }
            var exit = events.findFirstByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNullOrderByEventDatetimeAsc(
                    session.getIdRecordEnvironment(), event.getApprentice().getIdApprentice(), FacialEventType.EXIT);
            if (exit.isPresent() && (correction.attendanceStatus() == AttendanceStatus.ABSENT || !time.isBefore(exit.get().getEventDatetime())))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La corrección contradice la salida registrada");
        } else {
            var entry = events.findFirstByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNullOrderByEventDatetimeAsc(
                    session.getIdRecordEnvironment(), event.getApprentice().getIdApprentice(), FacialEventType.ENTRY)
                    .filter(e -> e.getAttendanceStatus() != AttendanceStatus.ABSENT && e.getEventDatetime().isBefore(time));
            if (entry.isEmpty() || correction.attendanceStatus() != entry.get().getAttendanceStatus())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La salida requiere una entrada válida y su mismo estado");
        }
        boolean excuse = correction.attendanceStatus() == AttendanceStatus.ABSENT && Boolean.TRUE.equals(correction.excuse());
        if (event.getAttendanceStatus() == correction.attendanceStatus() && event.getEventDatetime().isEqual(time)
                && Boolean.TRUE.equals(event.getExcuse()) == excuse) return new FacialEventResponseDTO(event);
        var mapper = new ObjectMapper();
        var before = mapper.createObjectNode();
        before.put("previousStatus", event.getAttendanceStatus().name()); before.put("previousDatetime", event.getEventDatetime().toString());
        before.put("previousExcuse", Boolean.TRUE.equals(event.getExcuse()));
        event.setAttendanceStatus(correction.attendanceStatus()); event.setEventDatetime(time); event.setExcuse(excuse);
        event.setUpdatedBy(actor.toString());
        events.saveAndFlush(event);
        if (event.getEventType() == FacialEventType.ENTRY) {
            events.findFirstByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNullOrderByEventDatetimeAsc(
                    session.getIdRecordEnvironment(), event.getApprentice().getIdApprentice(), FacialEventType.EXIT)
                    .ifPresent(exit -> { exit.setAttendanceStatus(event.getAttendanceStatus()); exit.setUpdatedBy(actor.toString()); events.saveAndFlush(exit); });
        }
        String actorName = names.fullName(actor);
        try {
            var meta = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(attendance.metadataFor(event, "corrected"));
            meta.setAll(before); meta.put("newStatus", event.getAttendanceStatus().name()); meta.put("newDatetime", time.toString());
            meta.put("newExcuse", excuse); meta.put("changedByName", actorName); meta.put("changedBy", actor.toString());
            meta.put("correctionReason", correction.reason()); meta.put("correctedAt", OffsetDateTime.now().toString());
            String message = "Se corrigió el registro de " + (event.getEventType() == FacialEventType.ENTRY ? "entrada" : "salida")
                    + ". Estado anterior: " + statusLabel(before.get("previousStatus").asText())
                    + " (" + displayTime(OffsetDateTime.parse(before.get("previousDatetime").asText())) + ", excusa: " + (before.get("previousExcuse").asBoolean() ? "sí" : "no") + "). Nuevo estado: "
                    + statusLabel(event.getAttendanceStatus().name()) + " (" + displayTime(time) + ", excusa: " + (excuse ? "sí" : "no") + ").\nModificado por: " + actorName
                    + ".\nMotivo: " + correction.reason() + ".\n" + attendance.attendanceContext(event);
            var recipients = new LinkedHashSet<>(List.of(event.getApprentice().getUser().getIdUser(), session.getInstructorScheduled().getUser().getIdUser()));
            for (UUID recipient : recipients) {
                boolean learner = recipient.equals(event.getApprentice().getUser().getIdUser());
                String recipientName = names.fullName(recipient);
                meta.put("recipientName", recipientName); meta.put("recipientContext", learner ? "learner" : "responsible_instructor");
                notices.createForRecipient(new CreateNotificationRequestDTO(recipient, "attendance_corrected", "Asistencia corregida",
                    (recipientName == null || recipientName.isBlank() ? "" : "Destinatario: " + recipientName + ".\n")
                            + message + (learner ? "\nRecibes este aviso porque se modificó tu asistencia." : "\nRecibes este aviso porque eres el instructor responsable seleccionado para esta sesión.")
                            + " Revisa el registro actualizado. Si no estás de acuerdo, contacta al coordinador.", session.getIdRecordEnvironment(), "record_environment", id, meta.toString()));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException(error); }
        return new FacialEventResponseDTO(event);
    }

    private String statusLabel(String status) {
        return switch (status) { case "ABSENT" -> "Inasistencia"; case "LATE" -> "Retraso"; case "PUNCTUAL" -> "Puntual"; default -> status; };
    }
    private String displayTime(OffsetDateTime time) {
        return time.atZoneSameInstant(java.time.ZoneId.of("America/Bogota"))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " (hora de Colombia)";
    }
}
