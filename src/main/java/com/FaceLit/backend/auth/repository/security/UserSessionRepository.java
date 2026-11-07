package com.FaceLit.backend.auth.repository.security;

import com.FaceLit.backend.auth.model.security.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE UserSession s SET s.sessionStatus = com.FaceLit.backend.auth.model.enums.SessionStatus.INACTIVE, s.endDate = :now WHERE s.user.idUser = :userId AND s.sessionStatus = com.FaceLit.backend.auth.model.enums.SessionStatus.ACTIVE")
    int revokeAll(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    // No necesita métodos personalizados por ahora
    // JpaRepository ya tiene save() que es lo único que necesitamos en el login

    // Verifica si el usuario ha iniciado sesion al menos una vez
    boolean existsByUser_IdUser(UUID idUser);

    List<UserSession> findByUser_IdUser(UUID idUser);

    // Busca si existe una sesión activa cuyo JWT aún no ha expirado
    // start_date + 8 horas > ahora = sesión vigente
    @Query("""
                SELECT COUNT(s) > 0 FROM UserSession s
                WHERE s.user.idUser = :userId
                AND s.sessionStatus = 'ACTIVE'
                AND s.startDate > :cutoff
            """)
    boolean hasActiveSession(
            @Param("userId") UUID userId,
            @Param("cutoff") OffsetDateTime cutoff);

}
