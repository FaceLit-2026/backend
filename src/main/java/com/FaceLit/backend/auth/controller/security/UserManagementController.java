package com.FaceLit.backend.auth.controller.security;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.auth.dto.request.security.UpdateUserRequestDTO;
import com.FaceLit.backend.auth.dto.request.security.CreateManagedUserRequestDTO;
import com.FaceLit.backend.auth.dto.response.security.UserDetailResponseDTO;
import com.FaceLit.backend.auth.service.security.UserManagementService;

import jakarta.validation.Valid;

// Gestion de usuario
@RestController
@RequestMapping("/api/admin/users")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    // GET /api/admin/users
    // Lista todos los usuarios registrados en la base de datos.
    @GetMapping
    public ResponseEntity<List<UserDetailResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(userManagementService.getAllUsers());
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countUsers() {
        return ResponseEntity.ok(userManagementService.getUserCounts());
    }

    // GET /api/admin/users/search?query=maria
    // Busca por nombre o correo
    @GetMapping("/search")
    public ResponseEntity<List<UserDetailResponseDTO>> search(
            @RequestParam String query) {
        return ResponseEntity.ok(userManagementService.searchUsers(query));
    }

    // GET /api/admin/users/{userId}
    // Ver detalle completo de un usuario
    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailResponseDTO> getDetail(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(userManagementService.getUserDetail(userId));
    }

    // POST /api/admin/users
    // Crear un coordinador desde la gestion administrativa
    @PostMapping
    public ResponseEntity<UserDetailResponseDTO> createUser(
            @Valid @RequestBody CreateManagedUserRequestDTO dto) {
        return ResponseEntity.ok(userManagementService.createUser(dto));
    }

    // PUT /api/admin/users/{userId}
    // Editar nombre, apellido, estado y rol
    @PutMapping("/{userId}")
    public ResponseEntity<UserDetailResponseDTO> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequestDTO dto) {
        return ResponseEntity.ok(userManagementService.updateUser(userId, dto));
    }

    // DELETE /api/admin/users/{userId}
    // Desactiva la cuenta de forma idempotente sin eliminar sus datos.
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID userId) {
        userManagementService.deactivateUser(userId);
        return ResponseEntity.noContent().build();
    }

}
