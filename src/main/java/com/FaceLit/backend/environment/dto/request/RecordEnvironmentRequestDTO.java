package com.FaceLit.backend.environment.dto.request;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecordEnvironmentRequestDTO {

    @NotNull(message = "El ambiente es obligatorio.")
    private UUID idEnvironment;

    @NotBlank(message = "El dispositivo es obligatorio.")
    @Size(max = 50, message = "El codigo del dispositivo no puede superar 50 caracteres.")
    private String deviceCode;

    @NotNull(message = "El instructor es obligatorio.")
    private UUID idInstructorInCharge;

    @NotNull(message = "La ficha es obligatoria.")
    private UUID idChip;

    @NotNull(message = "El tiempo de registro es obligatorio.")
    @Positive(message = "El tiempo de registro debe ser mayor a cero.")
    private Integer registrationMinutes;

    @NotNull(message = "La hora de entrada es obligatoria.")
    private OffsetDateTime entryTime;

    @NotNull(message = "La hora de salida es obligatoria.")
    private OffsetDateTime exitTime;

    @NotNull(message = "La hora de apagado es obligatoria.")
    private OffsetDateTime shutdownTime;
}
