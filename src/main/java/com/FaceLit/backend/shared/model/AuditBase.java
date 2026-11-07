package com.FaceLit.backend.shared.model;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.EntityListeners;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Column;


// Template de datos de auditoría: las entidades heredan estos campos comunes.
// No es un Template Method GoF porque no define un algoritmo con hooks sobrescribibles.
@MappedSuperclass  //  Anotacion que indica que voy a usar los atributos de esta clase,  pero no se va a crear en la base de datos. 
@EntityListeners(AuditingEntityListener.class) //  // Anotacion que indica que esta clase va a ser escuchada por el AuditingEntityListener, que es el encargado de llenar los campos de auditoria. 
@NoArgsConstructor  // Generar automaticamente los  contructores sin parametros
@Getter   // Generar automaticamente los Getter ( Obtener)
@Setter    //  Generar automaticamente los Setter ( Modificar)
    


public abstract class AuditBase {  // Auditoria Base

    /** Last persisted change across the records represented by one response. */
    public static OffsetDateTime latestUpdatedAt(AuditBase... records) {
        return java.util.Arrays.stream(records)
                .filter(java.util.Objects::nonNull)
                .flatMap(record -> java.util.stream.Stream.of(record.getCreatedAt(), record.getUpdatedAt()))
                .filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.comparing(OffsetDateTime::toInstant))
                .orElse(null);
    }

    @CreationTimestamp   // Fecha y hora en que el registro fue creado, Se asigna automáticamente al momento de persistir el registro.
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp  // Fecha y hora de la última modificación del registro,   Se actualiza automáticamente cada vez que el registro cambia. 
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // Pendiente hasta que haya login con JWT — por ahora siempre null
    @Column(name = "created_by",  length = 100)

  // ID del usuario que creó el registro.
    private String createdBy;

    // Pendiente hasta que haya login con JWT — por ahora siempre null
    @Column(name = "updated_by",  length = 100)   // ID del usuario que realizó la última modificación.
    private String updatedBy;

    // Soft delete — null mientras el registro esté activo
    @Column(name = "deleted_at", length = 100)  // Fecha y hora en que el registro fue eliminado lógicamente (soft delete).
    private OffsetDateTime deletedAt;

    // Soft delete — null mientras el registro esté activo
    @Column(name = "deleted_by", length = 100)   // ID del usuario que realizó la eliminación lógica.
    private String deletedBy;

}
