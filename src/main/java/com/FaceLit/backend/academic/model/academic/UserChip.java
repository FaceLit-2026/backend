package com.FaceLit.backend.academic.model.academic;

import java.util.UUID;

import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.shared.model.AuditBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "apprentice_chip", schema = "academic", indexes = {
        @Index(name = "idx_apprentice_chip_chip", columnList = "id_chip")
})
public class UserChip extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_apprentice_chip", nullable = false)
    private UUID idUserChip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_apprentice", nullable = false)
    private Apprentice apprentice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_chip", nullable = false)
    private Chip chip;

    @Transient
    private java.time.OffsetDateTime assignmentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private AcademicState state;

    public User getUser() {
        return apprentice != null ? apprentice.getUser() : null;
    }

    public java.time.OffsetDateTime getAssignmentDate() {
        return assignmentDate != null ? assignmentDate : getCreatedAt();
    }
}
