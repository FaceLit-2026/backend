package com.FaceLit.backend.facial.service;

import java.time.LocalDate;
import java.util.UUID;

import com.FaceLit.backend.facial.dto.request.FacialEventRequestDTO;
import com.FaceLit.backend.facial.dto.request.FacialAttendanceCaptureRequestDTO;
import com.FaceLit.backend.facial.dto.request.UpdateFacialEventExcuseRequestDTO;
import com.FaceLit.backend.facial.dto.response.AttendanceMatrixResponseDTO;
import com.FaceLit.backend.facial.dto.response.AttendanceStatusResponseDTO;
import com.FaceLit.backend.facial.dto.response.FacialEventResponseDTO;

public interface FacialEventService {

    FacialEventResponseDTO registerEvent(FacialEventRequestDTO dto, UUID authenticatedUserId);

    FacialEventResponseDTO registerEventFromImage(FacialAttendanceCaptureRequestDTO dto, UUID authenticatedUserId);

    void initializeSessionAbsences(UUID idRecordEnvironment, UUID authenticatedUserId);

    AttendanceStatusResponseDTO getAttendanceStatus(UUID idRecordEnvironment, UUID idApprentice, UUID authenticatedUserId);

    AttendanceMatrixResponseDTO getAttendanceMatrix(UUID idChip, LocalDate dateFrom, LocalDate dateTo, UUID authenticatedUserId);

    FacialEventResponseDTO updateExcuse(UpdateFacialEventExcuseRequestDTO dto, UUID authenticatedUserId);
}
