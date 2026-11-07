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

import com.FaceLit.backend.academic.dto.request.academic.ProgramRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.ProgramResponseDTO;
import com.FaceLit.backend.academic.service.academic.ProgramService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/academic/programs")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @PostMapping
    public ResponseEntity<ProgramResponseDTO> create(@Valid @RequestBody ProgramRequestDTO dto) {
        return ResponseEntity.ok(programService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<ProgramResponseDTO>> findAll() {
        return noStore(programService.findAll());
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProgramResponseDTO>> search(@RequestParam String name) {
        return noStore(programService.searchByName(name));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ProgramResponseDTO> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(programService.findByCode(code));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/{idProgram}")
    public ResponseEntity<ProgramResponseDTO> findById(@PathVariable UUID idProgram) {
        return ResponseEntity.ok(programService.findById(idProgram));
    }

    @PutMapping("/{idProgram}")
    public ResponseEntity<ProgramResponseDTO> update(
            @PathVariable UUID idProgram,
            @Valid @RequestBody ProgramRequestDTO dto) {
        return ResponseEntity.ok(programService.update(idProgram, dto));
    }

    @PatchMapping("/{idProgram}/reactivate")
    public ResponseEntity<ProgramResponseDTO> reactivate(@PathVariable UUID idProgram) {
        return ResponseEntity.ok(programService.reactivate(idProgram));
    }

    @DeleteMapping("/{idProgram}")
    public ResponseEntity<ProgramResponseDTO> delete(
            @PathVariable UUID idProgram,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(programService.delete(idProgram, reason));
    }
}