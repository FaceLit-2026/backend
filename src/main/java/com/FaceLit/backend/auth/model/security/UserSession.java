package com.FaceLit.backend.auth.model.security;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.auth.model.enums.SessionStatus;
import com.FaceLit.backend.shared.model.AuditBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// HU-01
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "user_session", schema = "security")
public class UserSession extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_user_session", nullable = false)
    private UUID idUserSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user_app", nullable = false)
    private User user;

    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 20)
    private SessionStatus sessionStatus;
}