package com.FaceLit.backend.academic.controller.academic;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.academic.dto.request.academic.ChipRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.ChipResponseDTO;
import com.FaceLit.backend.academic.service.academic.ChipService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/academic")
public class ChipController {

    private final ChipService chipService;

    public ChipController(ChipService chipService) {
        this.chipService = chipService;
    }

    @PostMapping("/programs/{idProgram}/chips")
    public ResponseEntity<ChipResponseDTO> create(
            @PathVariable UUID idProgram,
            @Valid @RequestBody ChipRequestDTO dto) {
        return ResponseEntity.ok(chipService.create(idProgram, dto));
    }

    @GetMapping("/programs/{idProgram}/chips")
    public ResponseEntity<List<ChipResponseDTO>> findByProgram(@PathVariable UUID idProgram) {
        return noStore(chipService.findByProgram(idProgram));
    }

    @GetMapping("/chips/{idChip}")
    public ResponseEntity<ChipResponseDTO> findById(@PathVariable UUID idChip) {
        return ResponseEntity.ok(chipService.findById(idChip));
    }

    @GetMapping("/chips/search")
    public ResponseEntity<List<ChipResponseDTO>> search(@RequestParam String code) {
        return noStore(chipService.searchByCode(code));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @PutMapping("/chips/{idChip}")
    public ResponseEntity<ChipResponseDTO> update(
            @PathVariable UUID idChip,
            @Valid @RequestBody ChipRequestDTO dto) {
        return ResponseEntity.ok(chipService.update(idChip, dto));
    }

    @PatchMapping("/chips/{idChip}/reactivate")
    public ResponseEntity<ChipResponseDTO> reactivate(@PathVariable UUID idChip) {
        return ResponseEntity.ok(chipService.reactivate(idChip));
    }

    @DeleteMapping("/chips/{idChip}")
    public ResponseEntity<ChipResponseDTO> delete(
            @PathVariable UUID idChip,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(chipService.delete(idChip, reason));
    }
}