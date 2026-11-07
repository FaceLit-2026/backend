package com.FaceLit.backend.facial.dto.response;

import java.util.UUID;

import com.FaceLit.backend.facial.model.enums.AttendanceStatus;

public record AttendanceStatusResponseDTO(
        UUID idRecordEnvironment,
        UUID idApprentice,
        AttendanceStatus attendanceStatus) {
}
