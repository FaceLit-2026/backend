package com.FaceLit.backend.academic.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.FaceLit.backend.academic.model.academic.Apprentice;

public interface ApprenticeRepository extends JpaRepository<Apprentice, UUID> {

    @EntityGraph(attributePaths = {"user", "user.credential"})
    Optional<Apprentice> findByUser_IdUser(UUID idUser);
}
