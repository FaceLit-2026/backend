package com.FaceLit.backend.environment.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EnvironmentAssignmentRequestDTO {
    @NotNull
    private UUID idChip;
}
