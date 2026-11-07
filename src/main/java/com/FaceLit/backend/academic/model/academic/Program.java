package com.FaceLit.backend.academic.model.academic;

import java.util.UUID;

import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.shared.model.AuditBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "program", schema = "academic")
public class Program extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_program", nullable = false)
    private UUID idProgram;

    @Column(name = "program_name", nullable = false, unique = true, length = 100)
    private String programName;

    @Column(name = "program_code", nullable = false, unique = true, length = 15)
    private String programCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private AcademicState state;

    @Column(name = "deactivation_reason", length = 200)
    private String deactivationReason;
}