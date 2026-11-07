package com.FaceLit.backend.auth.dto.response.roleandpermission;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class AssignRoleResponseDTO {

    private UUID userId;
    private String role;
    private String message;

    public static AssignRoleResponseDTO success(UUID userId, String role) {
        return new AssignRoleResponseDTO(userId, role, "Rol asignado correctamente"); 
    }

}
