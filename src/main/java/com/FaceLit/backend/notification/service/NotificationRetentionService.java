package com.FaceLit.backend.notification.service;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.FaceLit.backend.notification.repository.NotificationRepository;

/** Removes only notification rows older than the configured retention window. */
@Service
public class NotificationRetentionService {
    private static final int RETENTION_DAYS = 30;

    private final NotificationRepository notifications;

    public NotificationRetentionService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "America/Bogota")
    @Transactional
    public int deleteExpiredNotifications() {
        return notifications.deleteOlderThan(OffsetDateTime.now().minusDays(RETENTION_DAYS));
    }
}
