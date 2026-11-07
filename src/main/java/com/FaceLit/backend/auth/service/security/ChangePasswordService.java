package com.FaceLit.backend.auth.service.security;

import java.util.UUID;

import com.FaceLit.backend.auth.dto.request.security.ChangePasswordRequestDTO;
import com.FaceLit.backend.auth.dto.response.security.PasswordRecoveryResponseDTO;

public interface ChangePasswordService {

    PasswordRecoveryResponseDTO changePassword(UUID userId, ChangePasswordRequestDTO dto);
}