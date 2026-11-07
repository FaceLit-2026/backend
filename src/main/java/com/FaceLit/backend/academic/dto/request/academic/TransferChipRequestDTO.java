package com.FaceLit.backend.academic.dto.request.academic;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferChipRequestDTO {

    @NotNull(message = "La ficha destino es obligatoria")
    private UUID idNewChip;
}
