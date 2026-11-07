package com.FaceLit.backend.auth.dto.request.roleandpermission;

import jakarta.validation.constraints.NotNull;
import com.FaceLit.backend.auth.model.enums.RoleName; // Enum donde  manejo los roles
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignRoleRequestDTO {

    // El rol que el ADMINISTRATOR quiere asignar al usuario
    // Solo acepta: ADMINISTRATOR, INSTRUCTOR, APPRENTICE

    @NotNull(message = "El rol es obligatorio")
    private RoleName role; 
}
