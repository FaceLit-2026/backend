package com.FaceLit.backend.auth.controller.roleandpermission;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.auth.dto.request.roleandpermission.AssignRoleRequestDTO;
import com.FaceLit.backend.auth.dto.response.roleandpermission.AssignRoleResponseDTO;
import com.FaceLit.backend.auth.dto.response.security.UserListResponseDTO;
import com.FaceLit.backend.auth.service.roleandpermission.AdminRoleService;

import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;


@RestController
@RequestMapping("/api/admin")
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    public AdminRoleController(AdminRoleService adminRoleService) {
        this.adminRoleService = adminRoleService;
    }

    // PUT /api/admin/users/{userId}/role
    // Solo ADMINISTRATOR — cambia el rol de un usuario específico
    @PutMapping("/users/{userId}/role")
    
    public ResponseEntity<AssignRoleResponseDTO> assignRole(
            @PathVariable UUID userId,@Valid @RequestBody AssignRoleRequestDTO dto,
            @org.springframework.security.core.annotation.AuthenticationPrincipal UUID actorId) {
                return ResponseEntity.ok(adminRoleService.assignRole(userId, dto, actorId));
            }
            
}
