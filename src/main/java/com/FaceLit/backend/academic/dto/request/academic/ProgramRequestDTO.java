package com.FaceLit.backend.academic.dto.request.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProgramRequestDTO {

    @NotBlank(message = "El nombre del programa es obligatorio")
    @Size(max = 100, message = "El nombre del programa no puede superar 100 caracteres")
    private String programName;

    @NotBlank(message = "El código del programa es obligatorio")
    @Pattern(
            regexp = "^[A-Za-z0-9]{2,15}$",
            message = "El código de programa solo puede contener letras y números, sin espacios, entre 2 y 15 caracteres")
    private String programCode;
}