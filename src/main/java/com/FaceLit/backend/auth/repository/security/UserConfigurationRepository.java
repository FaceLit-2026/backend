package com.FaceLit.backend.auth.repository.security;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import com.FaceLit.backend.auth.model.security.UserConfiguration;

public interface UserConfigurationRepository extends JpaRepository<UserConfiguration, UUID> {

    // Busca la configuracion del usuario
    // Solo debe existir UNA configuracion por usuario
    Optional<UserConfiguration> findByUser_IdUser(UUID idUser);

    // Verifica si el usuario ya tiene configuracion creada
    boolean existsByUser_IdUser(UUID idUser);

}
