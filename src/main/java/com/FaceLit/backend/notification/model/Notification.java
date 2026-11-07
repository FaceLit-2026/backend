package com.FaceLit.backend.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "notification", schema = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_notification", nullable = false)
    private UUID idNotification;

    @Column(name = "id_user_app", nullable = false)
    private UUID idUserApp;

    @Column(name = "id_facial_event")
    private UUID idFacialEvent;

    @Column(name = "notification_type", nullable = false, length = 60)
    private String type;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "channel", nullable = false, length = 15)
    private String channel;

    @Column(name = "read_status", nullable = false)
    private boolean readStatus = false;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "reference_entity", length = 50)
    private String referenceEntity;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public void markRead() {
        if (!readStatus) {
            readStatus = true;
            readAt = OffsetDateTime.now();
        }
    }
}
