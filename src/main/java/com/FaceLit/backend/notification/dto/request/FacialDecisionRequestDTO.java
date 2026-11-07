package com.FaceLit.backend.notification.dto.request;

import jakarta.validation.constraints.Pattern;

public record FacialDecisionRequestDTO(
        @Pattern(regexp = "accepted|rejected") String decision
) {
}
