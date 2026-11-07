package com.FaceLit.backend.facial.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "face_reset_request", schema = "facialrecognition")
public class FaceResetRequest {
    @Id @Column(name = "id_face_reset_request")
    private UUID id = UUID.randomUUID();
    @Column(name = "id_user_app", nullable = false)
    private UUID userId;
    @Column(name = "request_status", nullable = false)
    private String status = "PENDING";
    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();
    @Column(name = "resolved_by")
    private UUID resolvedBy;
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
    @Column(name = "authorization_token")
    private UUID authorizationToken;
    @Column(name = "authorization_expires_at")
    private OffsetDateTime authorizationExpiresAt;
    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;
    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson;
    @Column(name = "request_notified", nullable = false)
    private boolean requestNotified;
    @Column(name = "decision_notified", nullable = false)
    private boolean decisionNotified;
    @Column(name = "completion_notified", nullable = false)
    private boolean completionNotified;

    public boolean canRegister() {
        return "ACCEPTED".equals(status) && consumedAt == null && authorizationExpiresAt != null
                && authorizationExpiresAt.isAfter(OffsetDateTime.now());
    }
}
