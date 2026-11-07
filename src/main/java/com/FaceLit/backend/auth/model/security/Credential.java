package com.FaceLit.backend.auth.model.security;

import java.util.UUID;
import java.time.OffsetDateTime;

import com.FaceLit.backend.auth.model.enums.CredentialStatus;
import com.FaceLit.backend.shared.model.AuditBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.GenerationType;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


// HU-01
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "credential", schema = "security", uniqueConstraints = { // Tabla credenciales
        @UniqueConstraint(name = "uk_credential_email", columnNames = "email") // El email es un valor unico.
})
public class Credential extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Generara el id automaticamente en la base de datos
    @Column(name = "id_credential", nullable = false) // el id no puede ser nulo.
    private UUID idCredential; // Id de credenciales

    @Column(name = "email", nullable = false, length = 255) // corrreo electronico.
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)   // contraseña del usuarios
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_status", nullable = false, length = 20) // estado de las credenciales
    private CredentialStatus credentialStatus;

    // ----------------------------------------------------------------------------------------------------------------------
    // Cuenta la cantidad de intentos fallidos de inicio de sesión consecutivos.
    // Se usa para proteger el sistema contra ataques de fuerza bruta.
    // Cuando alcanza un límite definido (por ejemplo, 3 intentos),
    // la cuenta puede ser bloqueada temporal o permanentemente.
    // Se reinicia a 0 cuando el usuario inicia sesión correctamente.
    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts = 0; // numero de intentos fallidos, se inicialisa en cero.

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @OneToOne
    @JoinColumn(name = "id_user_app", nullable = false, unique = true)
    private User user; // Relacion que tiene con la entidad e usuario de 1:1

}
