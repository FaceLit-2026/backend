package com.FaceLit.backend.facial.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Registration state queries over the existing shared schema. */
@Repository
public class FacialRegistrationStatusRepository {
    private final JdbcTemplate jdbc;

    public FacialRegistrationStatusRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean registered(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            """
                SELECT EXISTS (
                    SELECT 1
                    FROM academic.apprentice apprentice
                    JOIN facialrecognition.user_face user_face
                      ON user_face.id_apprentice = apprentice.id_apprentice
                    WHERE apprentice.id_user_app = ?
                      AND apprentice.deleted_at IS NULL
                      AND user_face.deleted_at IS NULL
                      AND user_face.status = 'ACTIVE'
                )
                """,
            Boolean.class, userId));
    }

    public OffsetDateTime registrationDate(UUID userId) {
        List<OffsetDateTime> dates = jdbc.queryForList("""
            SELECT COALESCE(user_face.updated_at, user_face.created_at) AS registration_date
            FROM academic.apprentice apprentice
            JOIN facialrecognition.user_face user_face
              ON user_face.id_apprentice = apprentice.id_apprentice
             AND user_face.deleted_at IS NULL
             AND user_face.status = 'ACTIVE'
            WHERE apprentice.id_user_app = ?
              AND apprentice.deleted_at IS NULL
            ORDER BY COALESCE(user_face.updated_at, user_face.created_at) DESC
            LIMIT 1
            """, OffsetDateTime.class, userId);
        return dates.isEmpty() ? null : dates.get(0);
    }

    public void lockUser(UUID userId) {
        jdbc.queryForObject("SELECT id_user_app FROM security.user_app WHERE id_user_app = ? FOR UPDATE", UUID.class, userId);
    }

    public boolean completionNotified(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM notification.notification
                WHERE id_user_app = ? AND notification_type = 'facial_registration_completed')
                """, Boolean.class, userId));
    }

    public boolean replacementConsumed(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM facialrecognition.face_reset_request
                WHERE id_user_app = ? AND consumed_at IS NOT NULL)
                """, Boolean.class, userId));
    }
}
