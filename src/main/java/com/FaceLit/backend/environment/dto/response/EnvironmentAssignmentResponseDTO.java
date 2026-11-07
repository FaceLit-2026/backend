package com.FaceLit.backend.environment.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.environment.model.ChipEnvironment;

import lombok.Getter;

@Getter
public class EnvironmentAssignmentResponseDTO {
    private final UUID id;
    private final UUID idChip;
    private final String fichaCode;
    private final OffsetDateTime assignedAt;

    public EnvironmentAssignmentResponseDTO(ChipEnvironment assignment) {
        this.id = assignment.getIdChipEnvironment();
        this.idChip = assignment.getChip().getIdChip();
        this.fichaCode = assignment.getChip().getChipCode();
        this.assignedAt = assignment.getCreatedAt();
    }
}
