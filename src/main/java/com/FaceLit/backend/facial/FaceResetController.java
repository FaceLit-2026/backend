package com.FaceLit.backend.facial;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/facial/reset-requests")
public class FaceResetController {
    private final FaceResetService service;
    public FaceResetController(FaceResetService service) { this.service = service; }

    @PostMapping
    public FaceResetService.ResetState create(@AuthenticationPrincipal UUID userId) { return service.create(userId); }

    @GetMapping("/me")
    public FaceResetService.ResetState current(@AuthenticationPrincipal UUID userId) { return service.current(userId); }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", ex.getReason()));
    }
}
