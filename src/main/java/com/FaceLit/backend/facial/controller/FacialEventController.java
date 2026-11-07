package com.FaceLit.backend.facial.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.facial.dto.request.FacialEventRequestDTO;
import com.FaceLit.backend.facial.dto.request.FacialAttendanceCaptureRequestDTO;
import com.FaceLit.backend.facial.dto.request.UpdateFacialEventExcuseRequestDTO;
import com.FaceLit.backend.facial.dto.response.AttendanceMatrixResponseDTO;
import com.FaceLit.backend.facial.dto.response.AttendanceStatusResponseDTO;
import com.FaceLit.backend.facial.dto.response.FacialEventResponseDTO;
import com.FaceLit.backend.facial.service.FacialEventService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/facial/events")
public class FacialEventController {

    private final FacialEventService facialEventService;

    public FacialEventController(FacialEventService facialEventService) {
        this.facialEventService = facialEventService;
    }

    @PostMapping
    public ResponseEntity<FacialEventResponseDTO> registerEvent(
            @Valid @RequestBody FacialEventRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(facialEventService.registerEvent(dto, principalId(principal)));
    }

    @PostMapping("/from-image")
    public ResponseEntity<FacialEventResponseDTO> registerEventFromImage(
            @Valid @RequestBody FacialAttendanceCaptureRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(facialEventService.registerEventFromImage(dto, principalId(principal)));
    }

    @PostMapping("/initialize-absences")
    public ResponseEntity<Void> initializeSessionAbsences(
            @RequestParam UUID idRecordEnvironment,
            @AuthenticationPrincipal Object principal) {
        facialEventService.initializeSessionAbsences(idRecordEnvironment, principalId(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attendance-status")
    public ResponseEntity<AttendanceStatusResponseDTO> getAttendanceStatus(
            @RequestParam UUID idRecordEnvironment,
            @RequestParam UUID idApprentice,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(facialEventService.getAttendanceStatus(idRecordEnvironment, idApprentice, principalId(principal)));
    }

    @GetMapping("/attendance-matrix")
    public ResponseEntity<AttendanceMatrixResponseDTO> getAttendanceMatrix(
            @RequestParam UUID idChip,
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(facialEventService.getAttendanceMatrix(idChip, dateFrom, dateTo, principalId(principal)));
    }

    @PatchMapping("/excuse")
    public ResponseEntity<FacialEventResponseDTO> updateExcuse(
            @Valid @RequestBody UpdateFacialEventExcuseRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(facialEventService.updateExcuse(dto, principalId(principal)));
    }

    private UUID principalId(Object principal) {
        return UUID.fromString(principal.toString());
    }
}
