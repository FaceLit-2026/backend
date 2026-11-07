package com.FaceLit.backend.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
// Aqui definimos las reglas de seguridad de nuestra API
// ES DECIR QUIEN PUEDE ENTRAR, A QUE PUEDE ACCEDER, COMO SE AUTENTICA
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Inyección por constructor — igual que en tus servicios
    private final JwtFilter jwtFilter;
    @org.springframework.beans.factory.annotation.Value("${facelit.allowed-origins:http://localhost:8081,http://localhost:19006}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, com.FaceLit.backend.auth.service.serviceImpl.security.RecoveryRateLimiter limiter) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Debes iniciar sesión\"}");
                }))
                .authorizeHttpRequests(auth -> auth

                        // ─── PÚBLICOS — no necesitan token ───────────────────────
                        .requestMatchers("/api/auth/logout", "/api/auth/change-password", "/api/auth/verify-password").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").hasRole("COORDINATOR")
                        // El token se valida como primer mensaje del canal WebSocket.
                        .requestMatchers("/ws/realtime").permitAll()

                        // ─── SOLO COORDINADOR ────────────────────────
                        // Ver todos los usuarios, asignar roles, gestionar todo
                        .requestMatchers("/api/admin/**").hasRole("COORDINATOR")
                        .requestMatchers("/api/facial/reset-requests/**").hasRole("APPRENTICE")
                        .requestMatchers("/api/facial/embeddings/from-image").hasRole("APPRENTICE")
                        .requestMatchers("/api/facial/embeddings/liveness-check").hasAnyRole("APPRENTICE", "INSTRUCTOR")
                        // Retired browser-vector enrollment used a different, incompatible schema.
                        .requestMatchers("/api/facial/challenge", "/api/facial/enrollment", "/api/facial/profile-photo").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/facial/events/attendance-status", "/api/facial/events/attendance-matrix")
                        .hasAnyRole("APPRENTICE", "INSTRUCTOR", "COORDINATOR")
                        .requestMatchers("/api/facial/events", "/api/facial/events/**")
                        .hasAnyRole("INSTRUCTOR", "COORDINATOR")
                        .requestMatchers(HttpMethod.GET, "/api/academic/me/**")
                        .hasAnyRole("COORDINATOR", "INSTRUCTOR", "APPRENTICE")
                        .requestMatchers(HttpMethod.GET, "/api/academic/programs/**", "/api/academic/chips/**")
                        .hasAnyRole("COORDINATOR", "INSTRUCTOR")
                        .requestMatchers("/api/academic/**").hasRole("COORDINATOR")
                        .requestMatchers(HttpMethod.GET, "/api/environment/environments", "/api/environment/environments/**")
                        .hasAnyRole("INSTRUCTOR", "COORDINATOR")
                        .requestMatchers(HttpMethod.POST, "/api/environment/environments")
                        .hasAnyRole("INSTRUCTOR", "COORDINATOR")
                        .requestMatchers(HttpMethod.POST, "/api/environment/environments/*/fichas")
                        .hasRole("COORDINATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/environment/environments/*/fichas/*")
                        .hasRole("COORDINATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/environment/environments/**")
                        .hasRole("COORDINATOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/environment/environments/**")
                        .hasRole("COORDINATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/environment/environments/**")
                        .hasRole("COORDINATOR")
                        .requestMatchers("/api/environment/**").hasRole("INSTRUCTOR")

                        // ─── PERFIL PERSONAL — cualquier usuario autenticado, sin importar rol ───
                        .requestMatchers("/api/profile/**").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .requestMatchers("/api/attendance/events").hasRole("COORDINATOR")

                        // Cualquier otro endpoint requiere autenticación
                        .anyRequest().authenticated())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Agregar el JwtFilter ANTES del filtro de autenticación de Spring
                // Así Spring Security ya sabe quién es el usuario antes de verificar permisos
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RecoveryRateLimitFilter(limiter), JwtFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
