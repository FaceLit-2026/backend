package com.FaceLit.backend.academic.controller.academic;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.academic.dto.request.academic.TransferChipRequestDTO;
import com.FaceLit.backend.academic.dto.request.academic.UserChipRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.UserChipResponseDTO;
import com.FaceLit.backend.academic.service.academic.UserChipService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/academic")
public class UserChipController {

    private final UserChipService userChipService;

    public UserChipController(UserChipService userChipService) {
        this.userChipService = userChipService;
    }

    @PostMapping("/chips/{idChip}/apprentices")
    public ResponseEntity<UserChipResponseDTO> assignInitialChip(
            @PathVariable UUID idChip,
            @Valid @RequestBody UserChipRequestDTO dto) {
        return ResponseEntity.ok(userChipService.assignInitialChip(idChip, dto));
    }

    @GetMapping("/chips/{idChip}/apprentices")
    public ResponseEntity<List<UserChipResponseDTO>> findApprenticesByChip(@PathVariable UUID idChip) {
        return ResponseEntity.ok(userChipService.findApprenticesByChip(idChip));
    }

    @GetMapping("/chips/apprentices")
    public ResponseEntity<List<UserChipResponseDTO>> findApprenticesByChips(@RequestParam List<UUID> ids) {
        return ResponseEntity.ok(userChipService.findApprenticesByChips(ids));
    }

    @GetMapping("/users/{idUser}/chip")
    public ResponseEntity<UserChipResponseDTO> getActiveChipByUser(@PathVariable UUID idUser) {
        return ResponseEntity.ok(userChipService.getActiveChipByUser(idUser));
    }

    @GetMapping("/me/chip")
    public ResponseEntity<UserChipResponseDTO> getAuthenticatedUserChip(
            @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(userChipService.getActiveChipByUser(UUID.fromString(principal.toString())));
    }

    @GetMapping("/users/{idUser}/chip/history")
    public ResponseEntity<List<UserChipResponseDTO>> getChipHistoryByUser(@PathVariable UUID idUser) {
        return ResponseEntity.ok(userChipService.getChipHistoryByUser(idUser));
    }

    @GetMapping("/users/{idUser}/chip/transfer-targets")
    public ResponseEntity<List<UserChipResponseDTO>> getTransferTargets(@PathVariable UUID idUser) {
        return ResponseEntity.ok(userChipService.getTransferTargets(idUser));
    }

    @PutMapping("/users/{idUser}/apprentice")
    public ResponseEntity<UserChipResponseDTO> updateApprentice(
            @PathVariable UUID idUser,
            @Valid @RequestBody UserChipRequestDTO dto) {
        return ResponseEntity.ok(userChipService.updateApprentice(idUser, dto));
    }

    @PostMapping("/users/{idUser}/chip/transfer")
    public ResponseEntity<UserChipResponseDTO> transferChip(
            @PathVariable UUID idUser,
            @Valid @RequestBody TransferChipRequestDTO dto) {
        return ResponseEntity.ok(userChipService.transferChip(idUser, dto));
    }
}
