package com.FaceLit.backend.environment.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.environment.model.RecordEnvironment;

import lombok.Getter;

@Getter
public class RecordEnvironmentResponseDTO {

    private final UUID idRecordEnvironment;
    private final UUID idEnvironment;
    private final String environmentName;
    private final UUID idDevice;
    private final String deviceCode;
    private final UUID idChip;
    private final String chipCode;
    private final UUID idInstructorScheduled;
    private final String instructorScheduledName;
    private final UUID idInstructorInCharge;
    private final String instructorInChargeName;
    private final String instructorName;
    private final OffsetDateTime sessionStart;
    private final OffsetDateTime entryTime;
    private final Integer registrationMinutes;
    private final OffsetDateTime exitTime;
    private final OffsetDateTime shutdownTime;
    private final Boolean active;
    private final String createdBy;
    private final String updatedBy;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public RecordEnvironmentResponseDTO(RecordEnvironment record) {
        this.idRecordEnvironment = record.getIdRecordEnvironment();
        this.idEnvironment = record.getEnvironment().getIdEnvironment();
        this.environmentName = record.getEnvironment().getEnvironmentName();
        this.idDevice = record.getDevice().getIdDevice();
        this.deviceCode = record.getDevice().getDeviceCode();
        this.idChip = record.getChip().getIdChip();
        this.chipCode = record.getChip().getChipCode();
        this.idInstructorScheduled = record.getInstructorScheduled().getIdInstructor();
        this.instructorScheduledName = "%s %s".formatted(
                record.getInstructorScheduled().getUser().getFirstName(),
                record.getInstructorScheduled().getUser().getLastName()).trim();
        this.idInstructorInCharge = record.getInstructorInCharge() == null
                ? null
                : record.getInstructorInCharge().getIdInstructor();
        this.instructorInChargeName = record.getInstructorInCharge() == null
                ? null
                : "%s %s".formatted(
                        record.getInstructorInCharge().getUser().getFirstName(),
                        record.getInstructorInCharge().getUser().getLastName()).trim();
        this.instructorName = this.instructorScheduledName;
        this.sessionStart = record.getEntryTime();
        this.entryTime = record.getEntryTime();
        this.registrationMinutes = record.getRegistrationMinutes();
        this.exitTime = record.getExitTime();
        this.shutdownTime = record.getShutdownTime();
        this.active = record.getActive();
        this.createdBy = record.getCreatedBy();
        this.updatedBy = record.getUpdatedBy();
        this.createdAt = record.getCreatedAt();
        this.updatedAt = record.getUpdatedAt();
    }
}
