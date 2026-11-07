package com.FaceLit.backend.notification.repository;

import com.FaceLit.backend.notification.model.EmailLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from EmailLog e where e.idNotification = :id")
    Optional<EmailLog> lockByNotification(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query("select e from EmailLog e, Notification n where n.idNotification = e.idNotification and n.type = 'attendance_exit_reminder' and e.sendStatus in :statuses")
    List<EmailLog> findExitRemindersToRetry(@org.springframework.data.repository.query.Param("statuses") List<EmailLog.SendStatus> statuses);
    Optional<EmailLog> findByIdNotification(UUID idNotification);
    List<EmailLog> findBySendStatus(EmailLog.SendStatus sendStatus);
}
