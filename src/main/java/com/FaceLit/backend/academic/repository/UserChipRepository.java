package com.FaceLit.backend.academic.repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.FaceLit.backend.academic.model.academic.UserChip;
import com.FaceLit.backend.academic.model.enums.AcademicState;

public interface UserChipRepository extends JpaRepository<UserChip, UUID> {

    boolean existsByApprentice_User_IdUserAndState(UUID idUser, AcademicState state);

    boolean existsByApprentice_IdApprenticeAndChip_IdChipAndState(
            UUID idApprentice,
            UUID idChip,
            AcademicState state);

    long countByChip_IdChip(UUID idChip);

    long countByChip_IdChipAndState(UUID idChip, AcademicState state);

    @Query(value = """
            SELECT COUNT(*)
            FROM academic.apprentice_chip apprentice_chip
            JOIN facialrecognition.user_face user_face
              ON user_face.id_apprentice = apprentice_chip.id_apprentice
            WHERE apprentice_chip.id_chip = :idChip
              AND apprentice_chip.state = 'ACTIVE'
              AND apprentice_chip.deleted_at IS NULL
              AND user_face.status = 'ACTIVE'
              AND user_face.deleted_at IS NULL
            """, nativeQuery = true)
    long countActiveRegisteredFacesByChip(@Param("idChip") UUID idChip);

    Optional<UserChip> findByApprentice_User_IdUserAndState(UUID idUser, AcademicState state);

    List<UserChip> findByChip_IdChip(UUID idChip);

    @EntityGraph(attributePaths = {"apprentice", "apprentice.user", "apprentice.user.credential", "chip", "chip.program"})
    List<UserChip> findByChip_IdChipInAndState(List<UUID> idChips, AcademicState state);

    @EntityGraph(attributePaths = {"apprentice", "apprentice.user", "chip", "chip.program"})
    List<UserChip> findByChip_IdChipAndState(UUID idChip, AcademicState state);

    List<UserChip> findByApprentice_User_IdUser(UUID idUser);
}
