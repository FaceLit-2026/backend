package com.FaceLit.backend.academic.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.FaceLit.backend.academic.model.academic.InstructorChip;

public interface InstructorChipRepository extends JpaRepository<InstructorChip, UUID> {

    boolean existsByInstructor_IdInstructorAndChip_IdChipAndActiveTrue(UUID idInstructor, UUID idChip);

    List<InstructorChip> findByInstructor_IdInstructorAndActiveTrue(UUID idInstructor);
}
