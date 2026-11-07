package com.FaceLit.backend.facial.dto.response;

import java.util.UUID;

public interface AttendanceMatrixRowProjection {
    UUID getRecordEnvironmentId();
    String getSessionDate();
    UUID getIdChip();
    String getChipCode();
    UUID getIdProgram();
    String getProgramName();
    UUID getIdApprentice();
    UUID getIdUser();
    String getFirstName();
    String getLastName();
    String getDocumentNumber();
    String getEnvironmentName();
    String getInstructorName();
    String getEntryTime();
    String getAttendanceStatus();
    UUID getIdFacialEvent();
    Boolean getExcuse();
    Integer getDelayMinutes();
}
