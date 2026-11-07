package com.FaceLit.backend.environment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.environment.model.ChipEnvironment;

public interface ChipEnvironmentRepository extends JpaRepository<ChipEnvironment, UUID> {
    List<ChipEnvironment> findByChip_IdChipAndStateAndDeletedAtIsNull(UUID idChip, AcademicState state);
    List<ChipEnvironment> findByEnvironment_IdEnvironmentAndStateAndDeletedAtIsNull(UUID idEnvironment, AcademicState state);
    Optional<ChipEnvironment> findByEnvironment_IdEnvironmentAndChip_IdChipAndStateAndDeletedAtIsNull(
            UUID idEnvironment, UUID idChip, AcademicState state);
}
