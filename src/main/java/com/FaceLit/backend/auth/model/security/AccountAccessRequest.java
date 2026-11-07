package com.FaceLit.backend.auth.model.security;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Durable delivery state; only a digest of the single-use link is retained. */
@Entity
@Getter
@Setter
@Table(name = "account_access_request", schema = "security")
public class AccountAccessRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_user_app", nullable = false)
    private User user;
    @Column(nullable = false) private String purpose;
    @Column(name = "target_email", nullable = false) private String targetEmail;
    @Column(name = "original_email", nullable = false) private String originalEmail;
    @Column(name = "token_hash", length = 64) private String tokenHash;
    @Column(name = "expires_at") private OffsetDateTime expiresAt;
    @Column(nullable = false) private boolean used;
    @Column(name = "send_status", nullable = false) private String sendStatus = "PENDING";
    @Column(nullable = false) private int attempts;
    @Column(name = "last_error") private String lastError;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "last_attempt_at") private OffsetDateTime lastAttemptAt;
}
