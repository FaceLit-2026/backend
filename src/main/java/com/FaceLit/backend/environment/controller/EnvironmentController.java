package com.FaceLit.backend.environment.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.academic.dto.response.academic.ChipResponseDTO;
import com.FaceLit.backend.academic.dto.response.academic.InstructorResponseDTO;
import com.FaceLit.backend.environment.dto.request.EnvironmentRequestDTO;
import com.FaceLit.backend.environment.dto.request.EnvironmentAssignmentRequestDTO;
import com.FaceLit.backend.environment.dto.request.RecordEnvironmentRequestDTO;
import com.FaceLit.backend.auth.dto.request.roleandpermission.PasswordVerificationRequestDTO;
import com.FaceLit.backend.environment.dto.response.EnvironmentResponseDTO;
import com.FaceLit.backend.environment.dto.response.RecordEnvironmentResponseDTO;
import com.FaceLit.backend.environment.dto.response.EnvironmentAssignmentResponseDTO;
import com.FaceLit.backend.environment.service.EnvironmentService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/environment")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @GetMapping("/environments")
    public ResponseEntity<List<EnvironmentResponseDTO>> searchEnvironments(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) UUID idChip) {
        if (idChip != null) return noStore(environmentService.findEnvironmentsForChip(idChip));
        return noStore(environmentService.searchEnvironments(search, includeInactive));
    }

    @PostMapping("/environments")
    public ResponseEntity<EnvironmentResponseDTO> getOrCreateEnvironment(
            @Valid @RequestBody EnvironmentRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(environmentService.getOrCreateEnvironment(dto, principalId(principal)));
    }

    @PutMapping("/environments/{idEnvironment}")
    public ResponseEntity<EnvironmentResponseDTO> updateEnvironment(
            @PathVariable UUID idEnvironment,
            @Valid @RequestBody EnvironmentRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(environmentService.updateEnvironment(idEnvironment, dto, principalId(principal)));
    }

    @PatchMapping("/environments/{idEnvironment}")
    public ResponseEntity<EnvironmentResponseDTO> patchEnvironment(
            @PathVariable UUID idEnvironment,
            @Valid @RequestBody EnvironmentRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(environmentService.updateEnvironment(idEnvironment, dto, principalId(principal)));
    }

    @DeleteMapping("/environments/{idEnvironment}")
    public ResponseEntity<Void> deleteEnvironment(
            @PathVariable UUID idEnvironment,
            @AuthenticationPrincipal Object principal) {
        environmentService.deactivateEnvironment(idEnvironment, principalId(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/environments/{idEnvironment}/fichas")
    public ResponseEntity<List<EnvironmentAssignmentResponseDTO>> findAssignments(@PathVariable UUID idEnvironment) {
        return noStore(environmentService.findAssignments(idEnvironment));
    }

    @PostMapping("/environments/{idEnvironment}/fichas")
    public ResponseEntity<EnvironmentAssignmentResponseDTO> assignFicha(
            @PathVariable UUID idEnvironment,
            @Valid @RequestBody EnvironmentAssignmentRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(environmentService.assignFicha(idEnvironment, dto, principalId(principal)));
    }

    @DeleteMapping("/environments/{idEnvironment}/fichas/{idChip}")
    public ResponseEntity<Void> unassignFicha(
            @PathVariable UUID idEnvironment,
            @PathVariable UUID idChip,
            @AuthenticationPrincipal Object principal) {
        environmentService.unassignFicha(idEnvironment, idChip, principalId(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/instructors-for-session")
    public ResponseEntity<List<InstructorResponseDTO>> findInstructorsForSession(
            @AuthenticationPrincipal Object principal) {
        return noStore(environmentService.findInstructorsForSession(principalId(principal)));
    }

    @GetMapping("/chips-for-instructor/{idInstructor}")
    public ResponseEntity<List<ChipResponseDTO>> findChipsForInstructor(@PathVariable UUID idInstructor,
            @AuthenticationPrincipal Object principal) {
        return noStore(environmentService.findChipsForInstructor(idInstructor, principalId(principal)));
    }

    @PostMapping("/sessions")
    public ResponseEntity<RecordEnvironmentResponseDTO> createSession(
            @Valid @RequestBody RecordEnvironmentRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(environmentService.createSession(dto, principalId(principal)));
    }

    @GetMapping("/sessions/active")
    public ResponseEntity<RecordEnvironmentResponseDTO> findCurrentActiveSession(
            @AuthenticationPrincipal Object principal) {
        Optional<RecordEnvironmentResponseDTO> session = environmentService
                .findCurrentActiveSession(principalId(principal));
        return session
                .map(value -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(value))
                .orElseGet(() -> ResponseEntity.noContent()
                        .cacheControl(CacheControl.noStore())
                        .build());
    }

    @PostMapping("/sessions/{idRecordEnvironment}/verify-exit-password")
    public ResponseEntity<Boolean> verifySessionExitPassword(
            @PathVariable UUID idRecordEnvironment,
            @RequestParam(defaultValue = "RESPONSIBLE") String credentialOwner,
            @Valid @RequestBody PasswordVerificationRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(environmentService.verifySessionExitPassword(
                idRecordEnvironment,
                principalId(principal),
                credentialOwner,
                dto.getPassword()));
    }

    @PutMapping("/sessions/{idRecordEnvironment}")
    public ResponseEntity<RecordEnvironmentResponseDTO> updateSession(
            @PathVariable UUID idRecordEnvironment,
            @Valid @RequestBody RecordEnvironmentRequestDTO dto,
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok(environmentService.updateSession(idRecordEnvironment, dto, principalId(principal)));
    }

    private UUID principalId(Object principal) {
        return UUID.fromString(principal.toString());
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
