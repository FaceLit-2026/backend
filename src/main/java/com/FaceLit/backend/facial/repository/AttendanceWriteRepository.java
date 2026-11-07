package com.FaceLit.backend.facial.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Bulk attendance writes; transaction ownership stays in the application service. */
@org.springframework.stereotype.Repository
public class AttendanceWriteRepository {
    private final JdbcTemplate jdbcTemplate;

    public AttendanceWriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initializeSessionAbsences(UUID authenticatedUserId, UUID idRecordEnvironment) {
        String sql = """
                INSERT INTO facialrecognition.facial_event (
                    id_record_environment, id_device, id_apprentice, event_datetime,
                    event_type, recognition_result, attendance_status, origin, excuse, created_by
                )
                SELECT re.id_record_environment, re.id_device, ac.id_apprentice, re.entry_time,
                       'ENTRY', 'ABSENT', 'ABSENT', 'PC', FALSE, ?
                FROM environment.record_environment re
                JOIN academic.apprentice_chip ac
                  ON ac.id_chip = re.id_chip
                 AND ac.state = 'ACTIVE'
                 AND ac.deleted_at IS NULL
                JOIN academic.apprentice a
                  ON a.id_apprentice = ac.id_apprentice
                 AND a.deleted_at IS NULL
                WHERE re.id_record_environment = ?
                  AND re.deleted_at IS NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM facialrecognition.facial_event fe
                      WHERE fe.id_record_environment = re.id_record_environment
                        AND fe.id_apprentice = ac.id_apprentice
                        AND fe.event_type = 'ENTRY'
                        AND fe.deleted_at IS NULL
                  )
                """;

        jdbcTemplate.update(sql, authenticatedUserId.toString(), idRecordEnvironment);
    }
}
