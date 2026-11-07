package com.FaceLit.backend.facial.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AttendanceMatrixResponseDTO(
        UUID idChip,
        String chipCode,
        UUID idProgram,
        String programName,
        List<AttendanceMatrixSessionDTO> sessions,
        List<AttendanceMatrixLearnerDTO> learners) {

    public record AttendanceMatrixSessionDTO(
            UUID idRecordEnvironment,
            LocalDate date,
            String environmentName,
            String instructorName) {
    }

    public record AttendanceMatrixLearnerDTO(
            UUID learnerId,
            UUID apprenticeId,
            String learnerName,
            String learnerDocument,
            List<AttendanceMatrixDayDTO> days) {
    }

    public record AttendanceMatrixDayDTO(
            UUID idFacialEvent,
            UUID idRecordEnvironment,
            LocalDate date,
            String status,
            String entryTime,
            Integer delayMinutes,
            String environmentName,
            String instructorName,
            String fichaNumber,
            String programName,
            Boolean exitRegistered,
            Boolean excuse,
            String sessionStartTime,
            String sessionEndTime,
            String exitTime,
            UUID idEnvironment) {
    }
}
