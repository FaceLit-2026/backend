package com.FaceLit.backend.auth.dto.request.security;

import com.FaceLit.backend.auth.model.enums.RoleName;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateManagedUserRequestDTO {

    @NotBlank(message = "El documento es obligatorio")
    @Pattern(regexp = "\\d{6,15}", message = "El documento debe tener entre 6 y 15 dígitos")
    @Size(max = 15, message = "El documento no puede superar 15 caracteres")
    private String numberDocument;

    @NotBlank(message = "El nombre es obligatorio")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+$", message = "El nombre solo puede contener letras")
    @Size(max = 60, message = "El nombre no puede superar 60 caracteres")
    private String firstName;

    @NotBlank(message = "El apellido es obligatorio")
    @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ ]+$", message = "El apellido solo puede contener letras")
    @Size(max = 60, message = "El apellido no puede superar 60 caracteres")
    private String lastName;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    @Size(max = 255, message = "El correo no puede superar 255 caracteres")
    private String email;

    @NotNull(message = "El rol es obligatorio")
    private RoleName role;
}
