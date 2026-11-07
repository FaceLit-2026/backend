package com.FaceLit.backend.facial.repository;

import java.time.LocalDate;
import java.util.UUID;

public record AttendanceMatrixRow(
            UUID recordEnvironmentId,
            LocalDate sessionDate,
            UUID idChip,
            String chipCode,
            UUID idProgram,
            String programName,
            UUID idApprentice,
            UUID idUser,
            String firstName,
            String lastName,
            String documentNumber,
            String environmentName,
            String instructorName,
            UUID idFacialEvent,
            String entryTime,
            String attendanceStatus,
            Boolean excuse,
            Integer delayMinutes,
            Boolean exitRegistered,
            String sessionStartTime,
            String sessionEndTime,
            String exitTime,
            UUID idEnvironment) {
    }
