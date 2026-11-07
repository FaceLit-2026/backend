package com.FaceLit.backend.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.FaceLit.backend.academic.model.academic.Program;
import com.FaceLit.backend.academic.model.enums.AcademicState;

public interface ProgramRepository extends JpaRepository<Program, UUID> {

    boolean existsByProgramNameIgnoreCase(String programName);
    boolean existsByProgramNameIgnoreCaseAndIdProgramNot(String programName, UUID idProgram);
    boolean existsByProgramCodeIgnoreCase(String programCode);
    boolean existsByProgramCodeIgnoreCaseAndIdProgramNot(String programCode, UUID idProgram);

    Optional<Program> findByProgramCodeIgnoreCase(String programCode);
    List<Program> findByProgramNameContainingIgnoreCase(String name);
    List<Program> findByState(AcademicState state);
}