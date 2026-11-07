package com.FaceLit.backend.academic.dto.response.academic;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.academic.model.academic.ChangeHistory;

import lombok.Getter;

@Getter
public class ChangeHistoryResponseDTO {
    private final UUID idChangeHistory;
    private final String entityName;
    private final UUID entityId;
    private final String fieldName;
    private final String oldValue;
    private final String newValue;
    private final String action;
    private final OffsetDateTime createdAt;
    private final String createdBy;

    public ChangeHistoryResponseDTO(ChangeHistory history) {
        this.idChangeHistory = history.getIdChangeHistory();
        this.entityName = history.getEntityName();
        this.entityId = history.getEntityId();
        this.fieldName = history.getFieldName();
        this.oldValue = history.getOldValue();
        this.newValue = history.getNewValue();
        this.action = history.getAction().name();
        this.createdAt = history.getCreatedAt();
        this.createdBy = history.getCreatedBy();
    }
}
