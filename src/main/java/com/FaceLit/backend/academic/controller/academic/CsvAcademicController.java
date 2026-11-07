package com.FaceLit.backend.academic.controller.academic;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.FaceLit.backend.academic.dto.response.academic.CsvUploadResponseDTO;
import com.FaceLit.backend.academic.dto.response.academic.PendingTransferResponseDTO;
import com.FaceLit.backend.academic.dto.response.academic.UserChipResponseDTO;
import com.FaceLit.backend.academic.service.academic.CsvAcademicService;

@RestController
@Validated
@RequestMapping("/api/academic/csv")
public class CsvAcademicController {

    private final CsvAcademicService csvAcademicService;

    public CsvAcademicController(CsvAcademicService csvAcademicService) {
        this.csvAcademicService = csvAcademicService;
    }

    @GetMapping("/template")
    public ResponseEntity<ByteArrayResource> template() {
        byte[] content = csvAcademicService.template();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("plantilla-carga-academica.csv").build());
        return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(content));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvUploadResponseDTO> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(csvAcademicService.upload(file));
    }

    @GetMapping("/pending-transfers")
    public ResponseEntity<List<PendingTransferResponseDTO>> pendingTransfers() {
        return ResponseEntity.ok(csvAcademicService.pendingTransfers());
    }

    @PostMapping("/pending-transfers/{id}/accept")
    public ResponseEntity<UserChipResponseDTO> accept(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(csvAcademicService.acceptPendingTransfer(id));
    }

    @PostMapping("/pending-transfers/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable("id") UUID id) {
        csvAcademicService.cancelPendingTransfer(id);
        return ResponseEntity.noContent().build();
    }
}
