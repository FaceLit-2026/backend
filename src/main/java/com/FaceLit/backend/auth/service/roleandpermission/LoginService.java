package com.FaceLit.backend.auth.service.roleandpermission;

import java.util.UUID;

import com.FaceLit.backend.auth.dto.request.roleandpermission.LoginRequestDTO;
import com.FaceLit.backend.auth.dto.response.roleandpermission.LoginResponseDTO;

public interface LoginService {

    // Recibe las credenciales del usuario y devuelve el JWT con rol y permisos
    LoginResponseDTO login(LoginRequestDTO dto);

    boolean verifyPassword(UUID userId, String password);

    boolean hasAcceptedPrivacy(String documentNumber);

}
