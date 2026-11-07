package com.FaceLit.backend.academic.model.academic;

import java.time.LocalDate;
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

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "instructor_chip", schema = "academic")
public class InstructorChip extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_instructor_chip", nullable = false)
    private UUID idInstructorChip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_instructor", nullable = false)
    private Instructor instructor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_chip", nullable = false)
    private Chip chip;

    @Column(name = "assignment_start")
    private LocalDate assignmentStart;

    @Column(name = "assignment_end")
    private LocalDate assignmentEnd;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
