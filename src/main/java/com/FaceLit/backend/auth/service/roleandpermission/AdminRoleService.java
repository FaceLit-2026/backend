package com.FaceLit.backend.auth.service.roleandpermission;

import java.util.List;
import java.util.UUID;

import com.FaceLit.backend.auth.dto.request.roleandpermission.AssignRoleRequestDTO;
import com.FaceLit.backend.auth.dto.response.roleandpermission.AssignRoleResponseDTO;
import com.FaceLit.backend.auth.dto.response.security.UserListResponseDTO;
public interface AdminRoleService {

     // Lista todos los usuarios registrados — solo ADMINISTRATOR puede verlos

      List<UserListResponseDTO> getAllUsers();

       // Cambia el rol de un usuario específico
    AssignRoleResponseDTO assignRole(UUID userId, AssignRoleRequestDTO dto, UUID actorId);

    default AssignRoleResponseDTO assignRole(UUID userId, AssignRoleRequestDTO dto) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        UUID actor = authentication != null && authentication.getPrincipal() instanceof UUID id ? id : null;
        return assignRole(userId, dto, actor);
    }

}
