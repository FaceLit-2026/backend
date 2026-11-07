package com.FaceLit.backend.academic.controller.academic;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.academic.dto.request.academic.InstructorRequestDTO;
import com.FaceLit.backend.academic.dto.response.academic.InstructorResponseDTO;
import com.FaceLit.backend.academic.service.academic.InstructorService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/academic")
public class InstructorController {

    private final InstructorService instructorService;

    public InstructorController(InstructorService instructorService) {
        this.instructorService = instructorService;
    }

    @PostMapping("/instructors")
    public ResponseEntity<InstructorResponseDTO> create(@Valid @RequestBody InstructorRequestDTO dto) {
        return ResponseEntity.ok(instructorService.create(dto));
    }

    @PutMapping("/instructors/{idInstructor}")
    public ResponseEntity<InstructorResponseDTO> update(
            @PathVariable UUID idInstructor,
            @Valid @RequestBody InstructorRequestDTO dto) {
        return ResponseEntity.ok(instructorService.update(idInstructor, dto));
    }

    @PatchMapping("/instructors/{idInstructor}/deactivate")
    public ResponseEntity<InstructorResponseDTO> deactivate(@PathVariable UUID idInstructor) {
        return ResponseEntity.ok(instructorService.deactivate(idInstructor));
    }

    @PatchMapping("/instructors/{idInstructor}/reactivate")
    public ResponseEntity<InstructorResponseDTO> reactivate(@PathVariable UUID idInstructor) {
        return ResponseEntity.ok(instructorService.reactivate(idInstructor));
    }

    @GetMapping("/instructors")
    public ResponseEntity<List<InstructorResponseDTO>> findAll() {
        return noStore(instructorService.findAll());
    }

    @GetMapping("/instructors/{idInstructor}")
    public ResponseEntity<InstructorResponseDTO> findById(@PathVariable UUID idInstructor) {
        return ResponseEntity.ok(instructorService.findById(idInstructor));
    }

    @GetMapping("/instructors/user/{idUser}")
    public ResponseEntity<InstructorResponseDTO> findByUser(@PathVariable UUID idUser) {
        return ResponseEntity.ok(instructorService.findByUser(idUser));
    }

    @GetMapping("/me/instructor")
    public ResponseEntity<InstructorResponseDTO> findAuthenticatedInstructor(
            @AuthenticationPrincipal Object principal) {
        return noStore(instructorService.findByUser(UUID.fromString(principal.toString())));
    }

    @GetMapping("/instructors/search")
    public ResponseEntity<List<InstructorResponseDTO>> search(
            @RequestParam(required = false) String document,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type) {
        return noStore(instructorService.search(document, name, type));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/instructors/eligible")
    public ResponseEntity<List<InstructorResponseDTO>> findEligibleByProgram(@RequestParam UUID idProgram) {
        return ResponseEntity.ok(instructorService.findEligibleByProgram(idProgram));
    }
}
