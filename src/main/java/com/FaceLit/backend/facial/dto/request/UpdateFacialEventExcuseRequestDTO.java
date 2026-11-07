package com.FaceLit.backend.facial.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateFacialEventExcuseRequestDTO {

    @NotNull(message = "La sesión es obligatoria.")
    private UUID idRecordEnvironment;

    @NotNull(message = "El aprendiz es obligatorio.")
    private UUID idApprentice;

    @NotNull(message = "La excusa es obligatoria.")
    private Boolean excuse;
}
