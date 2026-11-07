package com.FaceLit.backend.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateNotificationRequestDTO(
        @NotNull UUID recipientUserId,
        @NotBlank String type,
        String title,
        @NotBlank String message,
        UUID referenceId,
        String referenceEntity,
        UUID facialEventId,
        String metadataJson
) {
}
