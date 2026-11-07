package com.FaceLit.backend.academic.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.enums.AcademicState;

public interface ChipRepository extends JpaRepository<Chip, UUID> {

    boolean existsByChipCode(String chipCode);
    Optional<Chip> findByChipCode(String chipCode);
    List<Chip> findByChipCodeContainingIgnoreCase(String chipCode);
    List<Chip> findByState(AcademicState state);
    List<Chip> findByProgram_IdProgram(UUID idProgram);
    List<Chip> findByProgram_IdProgramIn(List<UUID> idPrograms);

    long countByProgram_IdProgram(UUID idProgram);
    long countByProgram_IdProgramAndState(UUID idProgram, AcademicState state);
}
