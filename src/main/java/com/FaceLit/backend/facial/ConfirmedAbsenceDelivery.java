package com.FaceLit.backend.facial;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.FaceLit.backend.facial.repository.FacialEventRepository;
import com.FaceLit.backend.facial.model.enums.AttendanceStatus;
import com.FaceLit.backend.facial.model.enums.FacialEventType;
import com.FaceLit.backend.facial.service.serviceImpl.FacialEventServiceImpl;
import com.FaceLit.backend.notification.repository.NotificationRepository;
import com.FaceLit.backend.notification.service.NotificationService;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ConfirmedAbsenceDelivery {
    private final FacialEventRepository events;
    private final NotificationRepository notifications;
    private final NotificationService delivery;
    private final FacialEventServiceImpl attendance;
    private final TransactionTemplate tx;
    public ConfirmedAbsenceDelivery(FacialEventRepository events, NotificationRepository notifications,
            NotificationService delivery, FacialEventServiceImpl attendance, TransactionTemplate tx) {
        this.events = events; this.notifications = notifications; this.delivery = delivery; this.attendance = attendance; this.tx = tx;
    }

    @Scheduled(fixedDelay = 60_000)
    public void scan() {
        for (UUID id : events.findUnnotifiedConfirmedAbsences(OffsetDateTime.now(), PageRequest.of(0, 100))) {
            try { deliver(id); }
            catch (RuntimeException error) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("No se pudo notificar inasistencia {}", id, error); }
        }
    }

    public void deliver(UUID id) {
        tx.executeWithoutResult(status -> {
            var event = events.lockForNotification(id).orElse(null);
            if (event == null || event.getDeletedAt() != null || event.getEventType() != FacialEventType.ENTRY
                    || event.getAttendanceStatus() != AttendanceStatus.ABSENT || Boolean.TRUE.equals(event.getExcuse())
                    || event.getRecordEnvironment().getDeletedAt() != null
                    || event.getRecordEnvironment().getShutdownTime().isAfter(OffsetDateTime.now())) return;
            if (notifications.existsByIdFacialEventAndType(id, "attendance_absent")) return;
            delivery.createForRecipient(new CreateNotificationRequestDTO(event.getApprentice().getUser().getIdUser(),
                    "attendance_absent", "Inasistencia confirmada", "La sesión finalizó y no registraste entrada.\n" + attendance.attendanceContext(event),
                    event.getRecordEnvironment().getIdRecordEnvironment(), "record_environment", id, attendance.metadataFor(event, "absent")));
        });
    }
}
