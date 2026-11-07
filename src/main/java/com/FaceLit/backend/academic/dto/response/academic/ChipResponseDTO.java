package com.FaceLit.backend.academic.dto.response.academic;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.academic.model.academic.Chip;

import lombok.Getter;

@Getter
// Adapter: transforma Chip y su relación con Program al contrato de respuesta.
public class ChipResponseDTO {

    private final UUID idChip;
    private final UUID idProgram;
    private final String programName;
    private final String programCode;
    private final String chipCode;
    private final String state;
    private final String deactivationReason;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public ChipResponseDTO(Chip chip) {
        this.idChip = chip.getIdChip();
        this.idProgram = chip.getProgram().getIdProgram();
        this.programName = chip.getProgram().getProgramName();
        this.programCode = chip.getProgram().getProgramCode();
        this.chipCode = chip.getChipCode();
        this.state = chip.getState().name();
        this.deactivationReason = chip.getDeactivationReason();
        this.createdAt = chip.getCreatedAt();
        this.updatedAt = chip.getUpdatedAt() != null ? chip.getUpdatedAt() : chip.getCreatedAt();
    }
}
