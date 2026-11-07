package com.FaceLit.backend.auth.service.serviceImpl.roleandpermission;

import com.FaceLit.backend.auth.service.roleandpermission.JwtService;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.shared.constants.AppConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
// Singleton gestionado por Spring: servicio stateless para crear y validar JWT.
public class JwtServiceImpl implements JwtService {
    private final com.FaceLit.backend.auth.service.security.UserSessionService sessions;
    private final com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService sessionSecurity;
    public JwtServiceImpl(com.FaceLit.backend.auth.service.security.UserSessionService sessions,
            com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService sessionSecurity) {
        this.sessions = sessions; this.sessionSecurity = sessionSecurity;
    }


    // Se lee desde application.yml — NUNCA hardcodeado
    @Value("${jwt.secret}")
    private String secret;

    // Se lee desde application.yml — ejemplo: 28800000 (8 horas en ms)
    @Value("${jwt.expiration}")
    private long expiration;

    // Convierte el secret en una clave segura para firmar el JWT
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public String generateToken(User user, String role, List<String> permissions) {
        var session = sessions.registerSession(user);
        return Jwts.builder().claim("sid", session.getIdUserSession().toString())
                // userId — para identificar al usuario en cada request
                .claim(AppConstants.JWT_CLAIM_USER_ID, user.getIdUser().toString())
                // email — útil para logs y auditoría
                .claim(AppConstants.JWT_CLAIM_EMAIL, user.getCredential().getEmail())
                // role — el frontend lo usa para redirigir al dashboard correcto
                .claim(AppConstants.JWT_CLAIM_ROLE, role)
                // permissions — el backend los usa para proteger endpoints
                .claim(AppConstants.JWT_CLAIM_PERMISSIONS, permissions)
                // subject — identificador principal del token
                .subject(user.getIdUser().toString())
                // fecha de emisión
                .issuedAt(new Date())
                // fecha de expiración — 8 horas desde ahora
                .expiration(new Date(System.currentTimeMillis() + expiration))
                // firma con el secret
                .signWith(getKey())
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            // Si no lanza excepción, el token es válido y no expiró
            Claims claims = getClaims(token);
            String sid = claims.get("sid", String.class);
            return sid != null && sessionSecurity.isActive(UUID.fromString(sid),
                    UUID.fromString(claims.get(AppConstants.JWT_CLAIM_USER_ID, String.class)),
                    claims.get(AppConstants.JWT_CLAIM_ROLE, String.class));
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public UUID extractUserId(String token) {
        return UUID.fromString(getClaims(token).get(AppConstants.JWT_CLAIM_USER_ID, String.class));
    }

    @Override
    public String extractRole(String token) {
        return getClaims(token).get(AppConstants.JWT_CLAIM_ROLE, String.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        return getClaims(token).get(AppConstants.JWT_CLAIM_PERMISSIONS, List.class);
    }

    @Override
    public String extractEmail(String token) {
        return getClaims(token).get(AppConstants.JWT_CLAIM_EMAIL, String.class);
    }

    @Override
    public void revokeToken(String token) {
        Claims claims = getClaims(token);
        String sid = claims.get("sid", String.class);
        if (sid != null) sessionSecurity.close(UUID.fromString(sid), extractUserId(token));
    }

    // Método interno — parsea y valida el token, extrae todos los claims
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // La firma se comprueba aquí; validateToken también consulta la sesión persistida.
    }

}
