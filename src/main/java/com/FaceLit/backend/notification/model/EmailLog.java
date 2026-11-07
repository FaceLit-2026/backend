package com.FaceLit.backend.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "email_log", schema = "notification")
public class EmailLog {

    public enum SendStatus {
        PENDING,
        SENT,
        FAILED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_email_log", nullable = false)
    private UUID idEmailLog;

    @Column(name = "id_notification", nullable = false, unique = true)
    private UUID idNotification;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_status", nullable = false, length = 20)
    private SendStatus sendStatus = SendStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public void registerSent() {
        sendStatus = SendStatus.SENT;
        sentAt = OffsetDateTime.now();
    }

    public void registerCancelled() { sendStatus = SendStatus.CANCELLED; }

    public void registerAttemptFailed() {
        attempts = attempts == null ? 1 : attempts + 1;
        sendStatus = SendStatus.FAILED;
    }
}
