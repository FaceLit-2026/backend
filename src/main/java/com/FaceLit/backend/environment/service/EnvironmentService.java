package com.FaceLit.backend.environment.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.FaceLit.backend.academic.dto.response.academic.ChipResponseDTO;
import com.FaceLit.backend.academic.dto.response.academic.InstructorResponseDTO;
import com.FaceLit.backend.environment.dto.request.EnvironmentRequestDTO;
import com.FaceLit.backend.environment.dto.request.RecordEnvironmentRequestDTO;
import com.FaceLit.backend.environment.dto.response.EnvironmentResponseDTO;
import com.FaceLit.backend.environment.dto.response.RecordEnvironmentResponseDTO;
import com.FaceLit.backend.environment.dto.request.EnvironmentAssignmentRequestDTO;
import com.FaceLit.backend.environment.dto.response.EnvironmentAssignmentResponseDTO;

public interface EnvironmentService {
    List<EnvironmentResponseDTO> findEnvironmentsForChip(UUID idChip);

    List<EnvironmentResponseDTO> searchEnvironments(String search);

    List<EnvironmentResponseDTO> searchEnvironments(String search, boolean includeInactive);

    EnvironmentResponseDTO getOrCreateEnvironment(EnvironmentRequestDTO dto, UUID authenticatedUserId);

    EnvironmentResponseDTO updateEnvironment(UUID idEnvironment, EnvironmentRequestDTO dto, UUID authenticatedUserId);

    void deactivateEnvironment(UUID idEnvironment, UUID authenticatedUserId);

    List<EnvironmentAssignmentResponseDTO> findAssignments(UUID idEnvironment);

    EnvironmentAssignmentResponseDTO assignFicha(UUID idEnvironment, EnvironmentAssignmentRequestDTO dto, UUID authenticatedUserId);

    void unassignFicha(UUID idEnvironment, UUID idChip, UUID authenticatedUserId);

    List<InstructorResponseDTO> findInstructorsForSession(UUID authenticatedUserId);

    List<ChipResponseDTO> findChipsForInstructor(UUID idInstructor, UUID authenticatedUserId);

    RecordEnvironmentResponseDTO createSession(RecordEnvironmentRequestDTO dto, UUID authenticatedUserId);

    Optional<RecordEnvironmentResponseDTO> findCurrentActiveSession(UUID authenticatedUserId);

    boolean verifySessionExitPassword(UUID idRecordEnvironment, UUID authenticatedUserId,
            String credentialOwner, String password);

    RecordEnvironmentResponseDTO updateSession(UUID idRecordEnvironment, RecordEnvironmentRequestDTO dto, UUID authenticatedUserId);
}
