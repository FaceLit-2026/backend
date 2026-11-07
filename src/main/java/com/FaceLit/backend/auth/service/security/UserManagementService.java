package com.FaceLit.backend.auth.service.security;

import java.util.List;
import java.util.UUID;
import com.FaceLit.backend.auth.dto.request.security.CreateManagedUserRequestDTO;
import com.FaceLit.backend.auth.dto.request.security.UpdateUserRequestDTO;
import com.FaceLit.backend.auth.dto.response.security.UserDetailResponseDTO;

// Gestion de usuario
public interface UserManagementService {

    // Lista todos los usuarios registrados en la base de datos.
    List<UserDetailResponseDTO> getAllUsers();

    long countUsers();

    java.util.Map<String, Long> getUserCounts();

    // Busca usuarios por nombre o correo (coincidencia parcial)
    List<UserDetailResponseDTO> searchUsers(String query);

    // Ver detalle completo de un usuario
    UserDetailResponseDTO getUserDetail(UUID userId);

    // Crear un coordinador desde la gestión administrativa
    UserDetailResponseDTO createUser(CreateManagedUserRequestDTO dto);

    // Editar nombre, apellido, estado y rol
    UserDetailResponseDTO updateUser(UUID userId, UpdateUserRequestDTO dto);

    // Desactivar la cuenta sin eliminar los datos del usuario.
    void deactivateUser(UUID userId);

}
