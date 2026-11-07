
package com.FaceLit.backend.academic.controller.academic;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.FaceLit.backend.academic.dto.response.academic.ChangeHistoryResponseDTO;
import com.FaceLit.backend.academic.repository.ChangeHistoryRepository;

@RestController
@RequestMapping("/api/academic/change-history")
public class ChangeHistoryController {
    private final ChangeHistoryRepository repository;

    public ChangeHistoryController(ChangeHistoryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ResponseEntity<List<ChangeHistoryResponseDTO>> findByEntity(
            @RequestParam String entityName,
            @RequestParam UUID entityId) {
        return ResponseEntity.ok(repository.findByEntityNameAndEntityIdOrderByCreatedAtDesc(entityName, entityId)
                .stream().map(ChangeHistoryResponseDTO::new).toList());
    }
}