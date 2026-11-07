package com.FaceLit.backend.notification.service.serviceImpl;

import com.FaceLit.backend.auth.repository.security.CredentialRepository;
import com.FaceLit.backend.notification.model.EmailLog;
import com.FaceLit.backend.notification.model.Notification;
import com.FaceLit.backend.notification.repository.EmailLogRepository;
import com.FaceLit.backend.notification.repository.NotificationRepository;
import com.FaceLit.backend.notification.service.EmailDispatchRequested;
import com.FaceLit.backend.shared.service.EmailService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import com.FaceLit.backend.environment.service.ExitReminderService;

@Service
public class EmailDispatchServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchServiceImpl.class);
    private static final int MAX_ATTEMPTS = 3;

    private final EmailLogRepository emailLogRepository;
    private final NotificationRepository notificationRepository;
    private final CredentialRepository credentialRepository;
    private final EmailService emailService;
    private final ExitReminderService exitReminders;
    private final TransactionTemplate transactions;

    public EmailDispatchServiceImpl(
            EmailLogRepository emailLogRepository,
            NotificationRepository notificationRepository,
            CredentialRepository credentialRepository,
            EmailService emailService, ExitReminderService exitReminders, TransactionTemplate transactions) {
        this.emailLogRepository = emailLogRepository;
        this.notificationRepository = notificationRepository;
        this.credentialRepository = credentialRepository;
        this.emailService = emailService;
        this.exitReminders = exitReminders;
        this.transactions = transactions;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailDispatchRequested(EmailDispatchRequested event) {
        dispatch(event.idNotification());
    }

    @Scheduled(fixedDelay = 15_000)
    public void retryExitReminders() {
        emailLogRepository.findExitRemindersToRetry(List.of(EmailLog.SendStatus.PENDING, EmailLog.SendStatus.FAILED))
                .forEach(this::retryIfUnderLimit);
    }

    private void dispatch(UUID id) { transactions.executeWithoutResult(tx -> sendAndLog(id)); }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void retryPendingEmails() {
        List<EmailLog> pending = emailLogRepository.findBySendStatus(EmailLog.SendStatus.PENDING);
        List<EmailLog> failed = emailLogRepository.findBySendStatus(EmailLog.SendStatus.FAILED);
        pending.forEach(this::retryIfUnderLimit);
        failed.forEach(this::retryIfUnderLimit);
    }

    private void retryIfUnderLimit(EmailLog emailLog) {
        if (emailLog.getAttempts() != null && emailLog.getAttempts() >= MAX_ATTEMPTS) {
            return;
        }
        dispatch(emailLog.getIdNotification());
    }

    @Transactional
    void sendAndLog(UUID idNotification) {
        Notification notification = notificationRepository.findById(idNotification).orElse(null);
        EmailLog emailLog = emailLogRepository.lockByNotification(idNotification).orElse(null);
        if (notification == null || emailLog == null || emailLog.getSendStatus() == EmailLog.SendStatus.SENT
                || emailLog.getSendStatus() == EmailLog.SendStatus.CANCELLED
                || (emailLog.getAttempts() != null && emailLog.getAttempts() >= MAX_ATTEMPTS)) {
            return;
        }
        if ("attendance_exit_reminder".equals(notification.getType())
                && !exitReminders.stillRelevant(notification, java.time.OffsetDateTime.now())) {
            emailLog.registerCancelled();
            emailLogRepository.save(emailLog);
            return;
        }

        credentialRepository.findByUser_IdUser(notification.getIdUserApp()).ifPresentOrElse(credential -> {
            try {
                emailService.sendNotificationEmail(
                        credential.getEmail(),
                        "FaceLit - " + notification.getTitle(),
                        notification.getMessage());
                emailLog.registerSent();
            } catch (Exception ex) {
                emailLog.registerAttemptFailed();
                log.error("Fallo envio correo notificacion {}: {}", idNotification, ex.getMessage());
            } finally {
                emailLogRepository.save(emailLog);
            }
        }, () -> {
            emailLog.registerAttemptFailed();
            emailLogRepository.save(emailLog);
            log.warn("Usuario sin credencial/correo para notificacion {}", idNotification);
        });
    }
}
