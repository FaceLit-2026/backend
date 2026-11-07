package com.FaceLit.backend.auth.model.security;

import java.util.UUID;

import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.shared.constants.AppConstants;
import com.FaceLit.backend.shared.model.AuditBase;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// HU-01
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "user_app", schema = "security",

        // Índice para acelerar búsquedas por número de documento
        indexes = {
                @Index(name = "idx_user_document", columnList = "number_document")
        },

        // Restricción de unicidad para evitar documentos duplicados
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_document", columnNames = "number_document")
        })
public class User extends AuditBase {

    // =========================================================
    // ID DEL USUARIO
    // =========================================================
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_user_app", nullable = false)
    private UUID idUser;

    // =========================================================
    // NÚMERO DE DOCUMENTO
    // No puede repetirse. En Colombia puede variar según tipo de documento.
    // =========================================================
    @Column(name = "number_document", nullable = false, length = AppConstants.DOCUMENT_NUMBER_MAX_LENGTH)
    private String documentNumber;

    // =========================================================
    // NOMBRE DEL USUARIO
    // =========================================================
    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    // =========================================================
    // APELLIDO DEL USUARIO
    // =========================================================
    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    // =========================================================
    // ESTADO DE LA CUENTA
    //
    // ACTIVE
    // INACTIVE
    // BLOCKED
    // =========================================================
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Column(name = "initial_password_prompt_seen", nullable = false)
    private boolean initialPasswordPromptSeen;

    @Column(name = "initial_password_changed", nullable = false)
    private boolean initialPasswordChanged;

    // =========================================================
    // RELACIÓN 1:1 CON CREDENTIAL
    //
    // Un usuario tiene una sola credencial.
    //
    // CascadeType.ALL:
    // - Crear usuario → crea credencial
    // - Actualizar usuario → actualiza credencial
    // - Eliminar usuario → elimina credencial
    // =========================================================
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Credential credential;

}
