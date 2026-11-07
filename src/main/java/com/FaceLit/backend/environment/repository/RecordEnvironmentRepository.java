package com.FaceLit.backend.environment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.FaceLit.backend.environment.model.RecordEnvironment;

public interface RecordEnvironmentRepository extends JpaRepository<RecordEnvironment, UUID> {
    @Query("select r.idRecordEnvironment from RecordEnvironment r where r.active = true and r.deletedAt is null and r.exitTime > :now and r.exitTime <= :limit")
    List<UUID> findExitReminderCandidates(@Param("now") OffsetDateTime now, @Param("limit") OffsetDateTime limit);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecordEnvironment r where r.idRecordEnvironment = :id")
    Optional<RecordEnvironment> lockForExitReminder(@Param("id") UUID id);

    @Modifying
    @Query("""
            UPDATE RecordEnvironment record
            SET record.active = false
            WHERE record.active = true
              AND record.shutdownTime <= :now
            """)
    int deactivateExpiredSessions(@Param("now") OffsetDateTime now);

    @Query("""
            SELECT record
            FROM RecordEnvironment record
            JOIN record.instructorScheduled scheduled
            LEFT JOIN record.instructorInCharge replacement
            WHERE record.active = true
              AND record.deletedAt IS NULL
              AND (
                    replacement.user.idUser = :authenticatedUserId
                    OR (
                        replacement IS NULL
                        AND scheduled.user.idUser = :authenticatedUserId
                    )
              )
              AND record.shutdownTime > CURRENT_TIMESTAMP
            ORDER BY record.entryTime DESC, record.createdAt DESC, record.idRecordEnvironment DESC
            """)
    List<RecordEnvironment> findCurrentActiveSessionsByInstructor(
            @Param("authenticatedUserId") UUID authenticatedUserId, org.springframework.data.domain.Pageable page);

    Optional<RecordEnvironment> findFirstByCreatedByAndActiveTrueAndDeletedAtIsNullAndShutdownTimeAfterOrderByEntryTimeDesc(
            String createdBy, OffsetDateTime now);

    @Query("""
            SELECT DISTINCT record.chip.idChip
            FROM RecordEnvironment record
            JOIN record.instructorScheduled scheduled
            LEFT JOIN record.instructorInCharge replacement
            WHERE record.deletedAt IS NULL
              AND (scheduled.idInstructor = :idInstructor OR replacement.idInstructor = :idInstructor)
            """)
    List<UUID> findDistinctChipIdsByInstructor(@Param("idInstructor") UUID idInstructor);

    @Query(value = """
            SELECT DISTINCT instructor_user.id_user_app
            FROM (
                SELECT scheduled.id_user_app
                FROM environment.record_environment record
                JOIN academic.instructor scheduled
                    ON scheduled.id_instructor = record.id_instructor_scheduled
                WHERE record.id_chip = :idChip
                UNION
                SELECT in_charge.id_user_app
                FROM environment.record_environment record
                JOIN academic.instructor in_charge
                    ON in_charge.id_instructor = record.id_instructor_in_charge
                WHERE record.id_chip = :idChip
                  AND record.id_instructor_in_charge IS NOT NULL
            ) instructor_user
            """, nativeQuery = true)
    List<UUID> findDistinctInstructorUserIdsByChip(@Param("idChip") UUID idChip);
}
