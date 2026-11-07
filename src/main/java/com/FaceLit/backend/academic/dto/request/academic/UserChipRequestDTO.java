package com.FaceLit.backend.academic.dto.request.academic;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserChipRequestDTO {

    private UUID idUser;

    @JsonAlias({"document", "documento"})
    @NotBlank(message = "El documento es obligatorio")
    private String documento;

    @JsonAlias({"name", "nombre"})
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @JsonAlias({"lastname", "apellido"})
    @NotBlank(message = "El apellido es obligatorio")
    private String apellido;

    @JsonAlias({"email", "correo"})
    @NotBlank(message = "El correo es obligatorio")
    private String correo;
}
