package com.FaceLit.backend.auth.service.roleandpermission;

import com.FaceLit.backend.auth.model.security.User;
import java.util.List;
import java.util.UUID;

public interface JwtService {

    // Genera el JWT con userId, email, rol y permisos
    String generateToken(User user, String role, List<String> permissions);

    // Valida que el token sea válido y no esté expirado
    boolean validateToken(String token);

    void revokeToken(String token);

    // Extrae el userId del payload del token
    UUID extractUserId(String token);

    // Extrae el rol del payload del token
    String extractRole(String token);

    // Extrae la lista de permisos del payload del token
    List<String> extractPermissions(String token);

    // Extrae el email del payload del token
    String extractEmail(String token);

}
