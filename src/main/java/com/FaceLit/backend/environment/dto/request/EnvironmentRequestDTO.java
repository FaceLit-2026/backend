package com.FaceLit.backend.environment.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import com.FaceLit.backend.environment.model.enums.EnvironmentState;

@Getter
@Setter
public class EnvironmentRequestDTO {

    @Size(max = 100, message = "El nombre del ambiente no puede superar 100 caracteres.")
    private String environmentName;

    private Integer capacity;

    private EnvironmentState state;
}
