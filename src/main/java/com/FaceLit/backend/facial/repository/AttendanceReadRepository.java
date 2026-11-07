package com.FaceLit.backend.facial.repository;

import com.FaceLit.backend.academic.model.academic.Instructor;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-side SQL for attendance; no authorization or HTTP decisions. */
@org.springframework.stereotype.Repository
public class AttendanceReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public AttendanceReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isInstructorAssigned(UUID idInstructor, UUID idChip) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM academic.instructor_chip
                        WHERE id_instructor = ? AND id_chip = ? AND active = TRUE AND deleted_at IS NULL)
                        OR EXISTS (SELECT 1 FROM environment.record_environment
                        WHERE id_chip = ? AND deleted_at IS NULL
                        AND (id_instructor_scheduled = ? OR id_instructor_in_charge = ?))
                    """, Boolean.class, idInstructor, idChip, idChip, idInstructor, idInstructor));
    }

    public String actorName(UUID actor) {
        var names = jdbcTemplate.queryForList("SELECT CONCAT(first_name, ' ', last_name) FROM security.user_app WHERE id_user_app = ?", String.class, actor);
        return names.isEmpty() ? "Instructor" : names.get(0);
    }

    public List<AttendanceMatrixRow> findAttendanceMatrixRows(
            UUID idChip,
            UUID idInstructor,
            UUID ownUserId,
            LocalDate dateFrom,
            LocalDate dateTo) {
        String sql = """
                WITH selected_sessions AS (
                    SELECT *
                    FROM (
                        SELECT
                            re.*,
                            ROW_NUMBER() OVER (
                                PARTITION BY CAST(re.entry_time AT TIME ZONE 'America/Bogota' AS date)
                                ORDER BY re.entry_time DESC
                            ) AS session_rank
                        FROM environment.record_environment re
                        WHERE re.id_chip = ?
                          AND re.deleted_at IS NULL
                          AND CAST(re.entry_time AT TIME ZONE 'America/Bogota' AS date) BETWEEN ? AND ?
                          AND (
                                CAST(? AS uuid) IS NULL
                             OR
                                re.id_instructor_scheduled = ?
                             OR re.id_instructor_in_charge = ?
                          )
                    ) ranked_sessions
                    WHERE ranked_sessions.session_rank = 1
                )
                SELECT
                    re.id_record_environment AS record_environment_id,
                    CAST(re.entry_time AT TIME ZONE 'America/Bogota' AS date) AS session_date,
                    c.id_chip,
                    c.chip_code,
                    p.id_program,
                    p.program_name,
                    a.id_apprentice,
                    u.id_user_app,
                    u.first_name,
                    u.last_name,
                    u.number_document,
                    env.environment_name,
                    CONCAT(instructor_user.first_name, ' ', instructor_user.last_name) AS instructor_name,
                    fe.id_facial_event,
                    re.id_environment,
                    TO_CHAR(re.entry_time AT TIME ZONE 'America/Bogota', 'HH24:MI') AS session_start_time,
                    TO_CHAR(re.exit_time AT TIME ZONE 'America/Bogota', 'HH24:MI') AS session_end_time,
                    CASE WHEN fex.attendance_status <> 'ABSENT'
                         THEN COALESCE(TO_CHAR(fex.event_datetime AT TIME ZONE 'America/Bogota', 'HH24:MI'), '')
                         ELSE '' END AS actual_exit_time,
                    CASE
                        WHEN fe.attendance_status = 'ABSENT' OR fe.event_datetime IS NULL THEN ''
                        ELSE TO_CHAR(fe.event_datetime AT TIME ZONE 'America/Bogota', 'HH24:MI')
                    END AS entry_time,
                    fe.attendance_status,
                    COALESCE(fe.excuse, FALSE) AS excuse,
                    CASE
                        WHEN fex.id_facial_event IS NOT NULL
                         AND fex.attendance_status IS NOT NULL
                         AND fex.attendance_status <> 'ABSENT'
                        THEN TRUE
                        ELSE FALSE
                    END AS exit_registered,
                    CASE
                        WHEN fe.attendance_status = 'ABSENT' OR fe.event_datetime IS NULL THEN 0
                        WHEN fe.event_datetime <= re.entry_time + (re.registration_minutes * INTERVAL '1 minute') THEN 0
                        ELSE FLOOR(EXTRACT(EPOCH FROM (fe.event_datetime - (re.entry_time + (re.registration_minutes * INTERVAL '1 minute')))) / 60)::INT
                    END AS delay_minutes
                FROM selected_sessions re
                JOIN academic.chip c
                  ON c.id_chip = re.id_chip
                JOIN academic.program p
                  ON p.id_program = c.id_program
                JOIN environment.environment env
                  ON env.id_environment = re.id_environment
                JOIN academic.instructor instructor
                  ON instructor.id_instructor = COALESCE(re.id_instructor_in_charge, re.id_instructor_scheduled)
                JOIN security.user_app instructor_user
                  ON instructor_user.id_user_app = instructor.id_user_app
                JOIN academic.apprentice_chip ac
                  ON ac.id_chip = re.id_chip
                 AND ac.state = 'ACTIVE'
                 AND ac.deleted_at IS NULL
                JOIN academic.apprentice a
                  ON a.id_apprentice = ac.id_apprentice
                 AND a.deleted_at IS NULL
                JOIN security.user_app u
                  ON u.id_user_app = a.id_user_app
                 AND u.deleted_at IS NULL
                LEFT JOIN facialrecognition.facial_event fe
                  ON fe.id_record_environment = re.id_record_environment
                 AND fe.id_apprentice = a.id_apprentice
                 AND fe.event_type = 'ENTRY'
                 AND fe.deleted_at IS NULL
                LEFT JOIN facialrecognition.facial_event fex
                  ON fex.id_record_environment = re.id_record_environment
                 AND fex.id_apprentice = a.id_apprentice
                 AND fex.event_type = 'EXIT'
                 AND fex.deleted_at IS NULL
                WHERE (CAST(? AS uuid) IS NULL OR u.id_user_app = ?)
                ORDER BY re.entry_time ASC, u.last_name ASC, u.first_name ASC, u.number_document ASC
                """;

        return jdbcTemplate.query(sql, ps -> {
            ps.setObject(1, idChip);
            ps.setObject(2, dateFrom);
            ps.setObject(3, dateTo);
            ps.setObject(4, idInstructor);
            ps.setObject(5, idInstructor);
            ps.setObject(6, idInstructor);
            ps.setObject(7, ownUserId);
            ps.setObject(8, ownUserId);
        }, (rs, rowNum) -> new AttendanceMatrixRow(
                rs.getObject("record_environment_id", UUID.class),
                rs.getObject("session_date", LocalDate.class),
                rs.getObject("id_chip", UUID.class),
                rs.getString("chip_code"),
                rs.getObject("id_program", UUID.class),
                rs.getString("program_name"),
                rs.getObject("id_apprentice", UUID.class),
                rs.getObject("id_user_app", UUID.class),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("number_document"),
                rs.getString("environment_name"),
                rs.getString("instructor_name"),
                rs.getObject("id_facial_event", UUID.class),
                rs.getString("entry_time"),
                rs.getString("attendance_status"),
                rs.getObject("excuse", Boolean.class),
                rs.getInt("delay_minutes"),
                rs.getBoolean("exit_registered"),
                rs.getString("session_start_time"),
                rs.getString("session_end_time"),
                rs.getString("actual_exit_time"),
                rs.getObject("id_environment", UUID.class)));
    }

}
