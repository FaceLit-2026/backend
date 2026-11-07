package com.FaceLit.backend.academic.service.csv;

import com.FaceLit.backend.academic.dto.response.academic.CsvUploadResponseDTO;
import com.FaceLit.backend.academic.model.academic.CsvPendingTransfer;
import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;
import com.FaceLit.backend.notification.service.ProfileChangeNotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** CSV notification composition and delivery; import decisions stay in the application services. */
@org.springframework.stereotype.Component
public class CsvImportNotifier {
    private static final Logger log = LoggerFactory.getLogger(CsvImportNotifier.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final ProfileChangeNotificationService profileChanges;

    public CsvImportNotifier(
            NotificationService notificationService,
            ObjectMapper objectMapper,
            ProfileChangeNotificationService profileChanges) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.profileChanges = profileChanges;
    }

    public void notifyCsvUpload(CsvUploadResponseDTO result) {
        try {
            Map<String, Integer> summary = Map.of(
                    "created", result.getCreados().size(),
                    "updated", result.getActualizados().size(),
                    "blocked", result.getInconsistenciasBloqueadas().size(),
                    "errors", result.getErroresDeReferencia().size());
            notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                    "csv_upload_done",
                    "Carga de CSV finalizada",
                    "Carga finalizada: " + summary.get("created") + " creados, "
                            + summary.get("updated") + " actualizados, "
                            + summary.get("blocked") + " pendientes de confirmacion, "
                            + summary.get("errors") + " con error.",
                    null,
                    null,
                    "csv_upload",
                    null,
                    json(Map.of("csvSummary", summary))));

            result.getInconsistenciasBloqueadas().forEach(item ->
                    notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                            "csv_inconsistency",
                            "Inconsistencia pendiente de revision",
                            item.mensaje(),
                            null,
                            null,
                            item.tipo(),
                            null,
                            json(Map.of(
                                    "conflictRecordType", item.tipo(),
                                    "fromValue", valueOrEmpty(item.valorActual()),
                                    "toValue", valueOrEmpty(item.valorArchivo()))))));

            result.getErroresDeReferencia().forEach(item ->
                    notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                            "csv_ref_error",
                            "Fila con error de referencia",
                            item.mensaje(),
                            null,
                            null,
                            item.tipo(),
                            null,
                            json(Map.of("row", item.fila(), "entityType", item.tipo())))));
        } catch (Exception exception) {
            log.warn("No se pudieron generar notificaciones de carga CSV", exception);
        }
    }

    public void notifyTransferApplied(CsvPendingTransfer pending) {
        notifyTransferDecision(
                "csv_transfer_applied",
                "Cambio de ficha aplicado",
                "El aprendiz " + fullName(pending.getUser()) + " fue movido de la ficha "
                        + pending.getCurrentChip().getChipCode() + " a la ficha "
                        + pending.getProposedChip().getChipCode() + ".",
                pending);
    }

    public void notifyTransferRejected(CsvPendingTransfer pending) {
        notifyTransferDecision(
                "csv_transfer_rejected",
                "Cambio de ficha rechazado",
                "Se decidio no aplicar el cambio de ficha del aprendiz " + fullName(pending.getUser())
                        + ". El registro no fue modificado.",
                pending);
    }

    private void notifyTransferDecision(String type, String title, String message, CsvPendingTransfer pending) {
        try {
            notificationService.createCoordinatorEvent(new MonolithEventRequestDTO(
                    type,
                    title,
                    message,
                    null,
                    pending.getIdPendingTransfer(),
                    "csv_pending_transfer",
                    null,
                    json(Map.of(
                            "learnerName", fullName(pending.getUser()),
                            "learnerDocument", pending.getUser().getDocumentNumber(),
                            "fromFichaNumber", pending.getCurrentChip().getChipCode(),
                            "toFichaNumber", pending.getProposedChip().getChipCode()))));
        } catch (Exception exception) {
            log.warn("No se pudo generar notificacion de traslado CSV {}", pending.getIdPendingTransfer(), exception);
        }
    }

    public void notifyCsvPersonalUpdate(CsvRow row, User user, String oldFirstName, String oldLastName, String oldEmail, String newEmail) {
        List<String> changes = new ArrayList<>();
        addChange(changes, "nombre", oldFirstName, user.getFirstName());
        addChange(changes, "apellido", oldLastName, user.getLastName());
        addChange(changes, "correo", oldEmail, newEmail);
        if (changes.isEmpty()) {
            return;
        }
        String type = row.value("tipo").equals("instructor") ? "instructor_profile_updated" : "apprentice_profile_updated";
        profileChanges.notifyChanges(user, type, row.value("tipo").equals("instructor") ? "instructor" : "learner", Map.of(
                "nombre", new String[]{oldFirstName, user.getFirstName()},
                "apellido", new String[]{oldLastName, user.getLastName()},
                "correo", new String[]{oldEmail, newEmail}));
    }

    public void notifyCsvInstructorPrograms(Instructor instructor, List<String> addedPrograms) {
        if (addedPrograms.isEmpty()) {
            return;
        }
        try {
            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    instructor.getUser().getIdUser(),
                    "instructor_assignment_updated",
                    "Tu asignacion academica cambio",
                    "Coordinacion agrego programas por carga CSV: " + String.join(", ", addedPrograms) + ".",
                    instructor.getIdInstructor(),
                    "instructor",
                    null,
                    json(Map.of(
                            "entityType", "instructor",
                            "entityId", instructor.getIdInstructor(),
                            "channel", "csv",
                            "addedPrograms", addedPrograms))));
        } catch (Exception exception) {
            log.warn("No se pudo generar notificacion de programas CSV para {}", instructor.getIdInstructor(), exception);
        }
    }

    private void addChange(List<String> changes, String field, String oldValue, String newValue) {
        String oldSafe = oldValue == null ? "" : oldValue;
        String newSafe = newValue == null ? "" : newValue;
        if (!oldSafe.equals(newSafe)) {
            changes.add(field + ": " + emptyAsNone(oldSafe) + " -> " + emptyAsNone(newSafe));
        }
    }

    private String emptyAsNone(String value) {
        return value == null || value.isBlank() ? "sin dato" : value;
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

}
