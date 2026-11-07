package com.FaceLit.backend.notification.repository;

import com.FaceLit.backend.notification.model.Notification;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    boolean existsByIdFacialEventAndType(UUID idFacialEvent, String type);
    List<Notification> findByReferenceEntityAndReferenceId(String referenceEntity, UUID referenceId);
    List<Notification> findByIdUserAppAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            UUID idUserApp, OffsetDateTime createdAt);
    List<Notification> findByIdUserAppOrderByCreatedAtDesc(UUID idUserApp);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
