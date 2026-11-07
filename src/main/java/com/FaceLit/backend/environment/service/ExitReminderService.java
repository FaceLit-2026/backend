package com.FaceLit.backend.environment.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.environment.repository.RecordEnvironmentRepository;
import com.FaceLit.backend.facial.repository.FacialEventRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.model.Notification;
import com.FaceLit.backend.notification.repository.NotificationRepository;
import com.FaceLit.backend.notification.repository.EmailLogRepository;
import com.FaceLit.backend.notification.model.EmailLog;
import com.FaceLit.backend.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExitReminderService {
    private static final Logger LOG = LoggerFactory.getLogger(ExitReminderService.class);
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private final RecordEnvironmentRepository sessions;
    private final FacialEventRepository attendance;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final TransactionTemplate transactions;
    private final EmailLogRepository emailLogs;
    private final ObjectMapper json = new ObjectMapper();

    public ExitReminderService(RecordEnvironmentRepository sessions, FacialEventRepository attendance,
            NotificationRepository notifications, NotificationService notificationService, TransactionTemplate transactions,
            EmailLogRepository emailLogs) {
        this.sessions = sessions; this.attendance = attendance; this.notifications = notifications;
        this.notificationService = notificationService; this.transactions = transactions;
        this.emailLogs = emailLogs;
    }

    @Scheduled(fixedDelay = 15_000)
    public void queueDueReminders() {
        OffsetDateTime now = OffsetDateTime.now();
        for (UUID id : sessions.findExitReminderCandidates(now, now.plusMinutes(5))) {
            try { queueSession(id, OffsetDateTime.now()); }
            catch (RuntimeException ex) { LOG.warn("Recordatorio de salida pendiente; se reintentará. session={}", id, ex); }
        }
    }

    public void queueSession(UUID id, OffsetDateTime now) {
        transactions.executeWithoutResult(tx -> {
            RecordEnvironment record = sessions.lockForExitReminder(id).orElse(null);
            if (!due(record, now)) return;
            // This is the instructor SELECTED in session configuration, not its creator.
            var instructor = record.getInstructorScheduled().getUser();
            Set<UUID> recipients = new LinkedHashSet<>(attendance.findExitReminderAttendees(id, now));
            recipients.add(instructor.getIdUser());
            String scheduleKey = record.getExitTime().toInstant().toString();
            var existing = notifications.findByReferenceEntityAndReferenceId("record_environment", id);
            var exit = record.getExitTime().atZoneSameInstant(ZONE);
            String details = "\nFicha: " + record.getChip().getChipCode()
                    + "\nInstructor responsable de la sesión: " + instructor.getFirstName() + " " + instructor.getLastName()
                    + "\nAmbiente: " + record.getEnvironment().getEnvironmentName()
                    + "\nFecha de la sesión: " + record.getEntryTime().atZoneSameInstant(ZONE).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + "\nHora de salida: " + exit.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " (hora de Colombia).";
            for (UUID recipient : recipients) {
                boolean queued = existing.stream().anyMatch(n -> recipient.equals(n.getIdUserApp())
                        && "attendance_exit_reminder".equals(n.getType()) && scheduleKey.equals(plannedExit(n))
                        && sameContext(record, n)
                        && emailLogs.findByIdNotification(n.getIdNotification())
                            .map(log -> log.getSendStatus() != EmailLog.SendStatus.CANCELLED).orElse(true));
                if (queued) continue;
                String message = recipient.equals(instructor.getIdUser())
                        ? "La hora de salida de tu sesión está próxima. Recuerda a los aprendices puntuales y con retraso que deben registrar su salida."
                        : "La hora de salida de tu sesión está próxima. Recuerda registrar tu salida a la hora configurada.";
                notificationService.createForRecipient(new CreateNotificationRequestDTO(recipient,
                        "attendance_exit_reminder", "Recordatorio: registra la salida de tu sesión", message + details,
                        id, "record_environment", null, metadata(record, scheduleKey)));
            }
            record.setExitReminderSent(true);
            sessions.save(record);
            // Keep checking the window: a learner who arrives late can still be notified once.
        });
    }

    public boolean stillRelevant(Notification notification, OffsetDateTime now) {
        RecordEnvironment record = sessions.lockForExitReminder(notification.getReferenceId()).orElse(null);
        if (!due(record, now) || !record.getExitTime().toInstant().toString().equals(plannedExit(notification))) return false;
        if (!sameContext(record, notification)) return false;
        if (record.getInstructorScheduled().getUser().getIdUser().equals(notification.getIdUserApp())) return true;
        // A reconfiguration/correction invalidates stale instructor, ficha or attendance data.
        return attendance.findExitReminderAttendees(record.getIdRecordEnvironment(), now).contains(notification.getIdUserApp());
    }

    private boolean due(RecordEnvironment record, OffsetDateTime now) {
        return record != null && record.getDeletedAt() == null && Boolean.TRUE.equals(record.getActive())
                && record.getExitTime() != null && !now.isBefore(record.getExitTime().minusMinutes(5))
                && now.isBefore(record.getExitTime());
    }

    private String metadata(RecordEnvironment record, String plannedExit) {
        try { return json.writeValueAsString(Map.of("plannedExit", plannedExit,
                "idInstructor", record.getInstructorScheduled().getIdInstructor().toString(),
                "idChip", record.getChip().getIdChip().toString(),
                "idEnvironment", record.getEnvironment().getIdEnvironment().toString())); }
        catch (Exception ex) { throw new IllegalStateException("No se pudo guardar el recordatorio", ex); }
    }

    private boolean sameContext(RecordEnvironment record, Notification n) {
        return String.valueOf(record.getInstructorScheduled().getIdInstructor()).equals(metadataValue(n, "idInstructor"))
                && String.valueOf(record.getChip().getIdChip()).equals(metadataValue(n, "idChip"))
                && String.valueOf(record.getEnvironment().getIdEnvironment()).equals(metadataValue(n, "idEnvironment"));
    }

    private String plannedExit(Notification notification) { return metadataValue(notification, "plannedExit"); }
    private String metadataValue(Notification notification, String field) {
        try { return json.readTree(notification.getMetadataJson()).path(field).asText(); }
        catch (Exception ex) { return null; }
    }
}
