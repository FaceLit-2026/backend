package com.FaceLit.backend.academic.dto.request.academic;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChipRequestDTO {

    @NotNull(message = "El programa es obligatorio")
    private UUID idProgram;

    @NotBlank(message = "El código de la ficha es obligatorio")
    @Pattern(regexp = "^[0-9]{7}$", message = "El código de la ficha debe tener 7 dígitos numéricos")
    private String chipCode;
}