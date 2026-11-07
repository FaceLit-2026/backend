package com.FaceLit.backend.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record MonolithEventRequestDTO(
        @NotBlank String type,
        String title,
        @NotBlank String message,
        UUID recipientUserId,
        UUID referenceId,
        String referenceEntity,
        UUID facialEventId,
        String metadataJson
) {
}
