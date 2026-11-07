package com.FaceLit.backend.notification.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponseDTO(
        UUID id,
        UUID recipientUserId,
        String type,
        String category,
        String channel,
        String title,
        String message,
        OffsetDateTime createdAt,
        boolean read,
        String meta,
        String emailStatus
) {
}
