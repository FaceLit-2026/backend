package com.FaceLit.backend.academic.dto.request.academic;

import java.util.List;
import java.util.UUID;

import com.FaceLit.backend.academic.model.enums.InstructorType;
import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstructorRequestDTO {

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

    @NotNull(message = "El tipo de instructor es obligatorio")
    private InstructorType instructorType;

    private List<UUID> programIds;
}
