package com.FaceLit.backend.auth.service.security;

import com.FaceLit.backend.auth.dto.request.security.RequestPasswordRecoveryDTO;
import com.FaceLit.backend.auth.dto.request.security.ResetPasswordDTO;
import com.FaceLit.backend.auth.dto.request.security.VerifyTokenDTO;
import com.FaceLit.backend.auth.dto.response.security.PasswordRecoveryResponseDTO;

public interface PasswordRecoveryService {

     // Genera el código de 6 dígitos y lo envía al correo
    // Si el correo no existe en BD → lanza PasswordRecoveryException
    PasswordRecoveryResponseDTO requestRecovery(RequestPasswordRecoveryDTO dto);

    // Valida el código sin consumirlo, antes de mostrar el formulario de nueva contraseña
    PasswordRecoveryResponseDTO verifyToken(VerifyTokenDTO dto);

    // Valida el código, compara que las contraseñas coincidan,
    // hashea y actualiza la contraseña en Credential
    PasswordRecoveryResponseDTO resetPassword(ResetPasswordDTO dto);

}
