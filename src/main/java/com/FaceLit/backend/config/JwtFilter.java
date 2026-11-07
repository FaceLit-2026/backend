package com.FaceLit.backend.config;

import java.util.stream.Collectors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/*
Este es el componente que intercepta cada request
antes de que llegue al controller, lee el token 
del header y le dice a Spring Security quién es
el usuario y qué permisos tiene.
*/

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.FaceLit.backend.auth.service.roleandpermission.JwtService;
import com.FaceLit.backend.shared.constants.AppConstants;

import java.io.IOException;
import java.util.List;
@Component
// Proxy/interceptor de seguridad: valida el JWT antes de que el request llegue al controller.
public class JwtFilter extends OncePerRequestFilter {

    // OncePerRequestFilter garantiza que el filtro se ejecuta
    // exactamente UNA vez por request — nunca dos veces

    private final JwtService jwtService;

    public JwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        if (java.util.Set.of("/api/auth/login", "/api/auth/request-recovery", "/api/auth/verify-token",
                "/api/auth/reset-password", "/api/auth/complete-access", "/api/auth/privacy-acceptance-status")
                .contains(request.getRequestURI().substring(request.getContextPath().length()))) {
            filterChain.doFilter(request, response); return;
        }

        // 1. Leer el header Authorization del request
        // El frontend manda: Authorization: Bearer eyJhbGci...
        String authHeader = request.getHeader(AppConstants.AUTHORIZATION_HEADER);

        // 2. Si no viene el header o no empieza con "Bearer ", dejar pasar
        // Los endpoints públicos (login, register) no mandan token
        if (authHeader == null || !authHeader.startsWith(AppConstants.BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extraer el token — quitar el prefijo "Bearer "
        String token = authHeader.substring(AppConstants.BEARER_PREFIX.length());

        // 4. Validar el token — firma correcta y no expirado
        if (!jwtService.validateToken(token)) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"La sesión expiró o fue revocada. Inicia sesión nuevamente.\"}");
            return;
        }

        // 5. Extraer rol y permisos del token
        String role = jwtService.extractRole(token);
        List<String> permissions = jwtService.extractPermissions(token);

        // 6. Construir las autoridades que Spring Security entiende
        // El rol lleva prefijo ROLE_ — así funciona hasRole() en Spring
        // Los permisos van directo — así funciona hasAuthority()
        //
        // Ejemplo resultado:
        // [ROLE_INSTRUCTOR, VIEW_OWN_PROFILE, VIEW_ATTENDANCE]
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(AppConstants.ROLE_PREFIX + role));
        authorities.addAll(
                permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()));

        // 7. Crear el objeto de autenticación para Spring Security
        // null en las credenciales — ya no las necesitamos, el token es suficiente
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                jwtService.extractUserId(token), // principal = userId
                null, // credentials = null
                authorities // roles y permisos
        );

        // 8. Registrar la autenticación en el contexto de seguridad
        // A partir de aquí Spring Security sabe quién es el usuario
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 9. Continuar con el request
        filterChain.doFilter(request, response);
    }

}
