package com.FaceLit.backend.academic.dto.response.academic;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.Program;

import lombok.Getter;

@Getter
// Adapter: evita exponer directamente la entidad JPA Program en la API.
public class ProgramResponseDTO {

    private final UUID idProgram;
    private final String programName;
    private final String programCode;
    private final String state;
    private final String deactivationReason;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final List<ChipResponseDTO> chips;
    private final List<UUID> chipIds;
    private final List<String> chipCodes;

    public ProgramResponseDTO(Program program) {
        this(program, List.of());
    }

    public ProgramResponseDTO(Program program, List<Chip> chips) {
        this.idProgram = program.getIdProgram();
        this.programName = program.getProgramName();
        this.programCode = program.getProgramCode();
        this.state = program.getState().name();
        this.deactivationReason = program.getDeactivationReason();
        this.createdAt = program.getCreatedAt();
        this.updatedAt = program.getUpdatedAt() != null ? program.getUpdatedAt() : program.getCreatedAt();
        this.chips = chips == null ? List.of() : chips.stream().map(ChipResponseDTO::new).toList();
        this.chipIds = this.chips.stream().map(ChipResponseDTO::getIdChip).toList();
        this.chipCodes = this.chips.stream().map(ChipResponseDTO::getChipCode).toList();
    }
}
