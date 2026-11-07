package com.FaceLit.backend.environment.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.environment.model.Environment;

import lombok.Getter;

@Getter
public class EnvironmentResponseDTO {

    private final UUID idEnvironment;
    private final String environmentName;
    private final Integer capacity;
    private final String state;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public EnvironmentResponseDTO(Environment environment) {
        this.idEnvironment = environment.getIdEnvironment();
        this.environmentName = environment.getEnvironmentName();
        this.capacity = environment.getCapacity();
        this.state = environment.getState().name();
        this.createdAt = environment.getCreatedAt();
        this.updatedAt = environment.getUpdatedAt();
    }
}
