package com.FaceLit.backend.auth.model.security;

import java.time.OffsetDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auth_rate_limit", schema = "security")
@Getter @Setter
public class AuthRateLimit {
    @Id @Column(name = "bucket_key", length = 64)
    private String key;
    @Column(name = "window_start", nullable = false)
    private OffsetDateTime windowStart;
    @Column(nullable = false)
    private int attempts;
}
