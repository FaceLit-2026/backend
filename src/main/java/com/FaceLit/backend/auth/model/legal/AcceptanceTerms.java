package com.FaceLit.backend.auth.model.legal;

import java.util.UUID;

import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.shared.model.AuditBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import jakarta.persistence.FetchType;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "terms_acceptance", schema = "legal")
@Getter
@Setter
@NoArgsConstructor
public class AcceptanceTerms extends AuditBase {

    // HU-02
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_acceptance", nullable = false)
    private UUID idAcceptance;

    // true = aceptó, false = no aceptó
    @Column(name = "accepted", nullable = false)
    private boolean accepted = false;

    // Identifies the privacy notice version for which this decision was made.
    @Column(name = "notice_version", nullable = false, length = 64)
    private String noticeVersion;

    // Relación con el usuario que tomó la decisión sobre el aviso.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user_app", nullable = false)
    private User user;

}
