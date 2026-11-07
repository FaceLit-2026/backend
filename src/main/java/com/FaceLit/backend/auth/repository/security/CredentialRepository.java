package com.FaceLit.backend.auth.repository.security;

import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.model.security.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Credential c where c.user.idUser = :userId")
    Optional<Credential> lockByUserId(@org.springframework.data.repository.query.Param("userId") UUID userId);

    // Validar si ya existe una credencial con ese email
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);

    // Busacr credenciales por email
    Optional<Credential> findByEmail(String email); // Opcional es una clase que puede contener un valor o vacio.
    Optional<Credential> findByEmailIgnoreCase(String email);

    Optional<Credential> findByUser_DocumentNumber(String documentNumber);

    // En auth/repository/security/CredentialRepository.java
    Optional<Credential> findByUser(User user);

    Optional<Credential> findByUser_IdUser(UUID idUser);

}
