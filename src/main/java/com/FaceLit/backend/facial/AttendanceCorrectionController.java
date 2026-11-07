package com.FaceLit.backend.facial;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import com.FaceLit.backend.facial.dto.response.FacialEventResponseDTO;

@RestController
@RequestMapping("/api/facial/events")
public class AttendanceCorrectionController {
    private final AttendanceCorrectionService service;
    public AttendanceCorrectionController(AttendanceCorrectionService service) { this.service = service; }
    @PatchMapping("/{id}/correction")
    public FacialEventResponseDTO correct(@PathVariable UUID id, @RequestBody @Valid AttendanceCorrectionService.Correction correction,
            @AuthenticationPrincipal UUID actor) { return service.correct(id, correction, actor); }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> handleError(org.springframework.web.server.ResponseStatusException error) {
        return org.springframework.http.ResponseEntity.status(error.getStatusCode())
                .body(java.util.Map.of("message", error.getReason() == null ? "Solicitud inválida" : error.getReason()));
    }
}
