package com.FaceLit.backend.facial.dto.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacialEventRequestDTO {

    @NotNull(message = "La sesión es obligatoria.")
    private UUID idRecordEnvironment;

    @NotNull(message = "El dispositivo es obligatorio.")
    private UUID idDevice;

    @NotNull(message = "El aprendiz es obligatorio.")
    private UUID idApprentice;

    private OffsetDateTime eventDatetime;

    // Optional for older clients. An early exit must be requested explicitly.
    private com.FaceLit.backend.facial.model.enums.FacialEventType eventType;

    @NotBlank(message = "El resultado del reconocimiento es obligatorio.")
    @Size(max = 20, message = "El resultado del reconocimiento no puede superar 20 caracteres.")
    private String recognitionResult;

    @DecimalMin(value = "0.0000", message = "El puntaje debe ser mayor o igual a 0.")
    @DecimalMax(value = "1.0000", message = "El puntaje debe ser menor o igual a 1.")
    private BigDecimal matchScore;

    @NotNull(message = "El origen es obligatorio.")
    private String origin;
}
