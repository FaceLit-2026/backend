package com.FaceLit.backend.auth.model.security;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.shared.model.AuditBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// HU-05
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "password_recovery", schema = "security")
public class PasswordRecovery extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_password_recovery", nullable = false)
    private UUID idPasswordRecovery;

    // Relación N:1 — un usuario puede tener varios intentos de recuperación
    // pero solo uno activo a la vez
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user_app", nullable = false)
    private User user;

    // Código de seis dígitos enviado al destinatario de esta recuperación.
    @Column(name = "token", nullable = false, length = 255)
    private String token;



    // Fecha en que expira el token — 5 minutos después del request_date
    @Column(name = "expiration_date", nullable = false)
    private OffsetDateTime expirationDate;

    // false = token activo, aún no fue usado
    // true = token ya fue usado, no puede reutilizarse
    @Column(name = "used", nullable = false)
    private boolean used = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "access_request_id")
    private AccountAccessRequest accessRequest;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "destination_email", length = 255)
    private String destinationEmail;
    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts;
    @Column(name = "reset_token_hash", length = 64)
    private String resetTokenHash;



    // Verifica si el token sigue siendo válido
    // Un código usado o vencido deja de ser válido.
    public boolean isCurrent() {
        return !used && OffsetDateTime.now().isBefore(expirationDate);
        // Retorna true si:
        // !used           → NO ha sido usado
        // && isBefore()   → Y todavía no ha expirado
}

}
