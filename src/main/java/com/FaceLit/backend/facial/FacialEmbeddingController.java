package com.FaceLit.backend.facial;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.FaceLit.backend.facial.FacialEmbeddingRegistrationService.Registration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Authenticated bridge for the existing client capture flow. */
@RestController
@RequestMapping("/api/facial/embeddings")
public class FacialEmbeddingController {
    private final FacialEmbeddingVerificationClient client;

    private final FacialEmbeddingRegistrationService registrationService;

    public FacialEmbeddingController(FacialEmbeddingVerificationClient client,
                                     FacialEmbeddingRegistrationService registrationService) {
        this.client = client;
        this.registrationService = registrationService;
    }

    public record Liveness(
            @JsonProperty("image_frames") @NotNull @Size(min = 3, max = 18) List<@NotBlank @Size(max = 5000000) String> imageFrames,
            @JsonProperty("liveness_challenge") @Size(max = 40) String livenessChallenge) {}

    @Schema(name = "FacialRegistrationResponse", description = "Registration metadata without the biometric vector.")
    private record RegistrationResult(
            UUID id,
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("embedding_dimension") int embeddingDimension,
            @JsonProperty("model_name") String modelName,
            @JsonProperty("photo_reference") @Schema(nullable = true) String photoReference,
            String status,
            @JsonProperty("created_at") OffsetDateTime createdAt) {}

    @Schema(name = "FacialLivenessResponse")
    private record LivenessResult(boolean live, String reason) {}

    @Schema(name = "FacialServiceError")
    private record FacialError(String message) {}

    @ApiResponse(responseCode = "201", description = "Facial registration created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationResult.class)))
    @ApiResponse(responseCode = "409", description = "The face is already registered to another account",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FacialError.class)))
    @ApiResponse(responseCode = "502", description = "Facial service unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FacialError.class)))
    @PostMapping("/from-image")
    public ResponseEntity<?> register(@Valid @RequestBody Registration request, @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request, userId));
    }

    @ApiResponse(responseCode = "200", description = "Liveness evaluated, including negative results",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LivenessResult.class)))
    @ApiResponse(responseCode = "502", description = "Facial service unavailable",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FacialError.class)))
    @PostMapping("/liveness-check")
    public JsonNode checkLiveness(@Valid @RequestBody Liveness request) {
        return client.checkLiveness(request);
    }

    @ExceptionHandler(FacialEmbeddingRegistrationService.OwnershipException.class)
    public ResponseEntity<Map<String, String>> ownershipError(FacialEmbeddingRegistrationService.OwnershipException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", error.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> remoteError(RestClientResponseException error) {
        int status = error.getStatusCode().value();
        // A rejected service credential is an integration failure, not an expired user session.
        if (status == 401 || status == 403 || status >= 500) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "El servicio facial no está disponible."));
        }
        String message = "No fue posible procesar la captura facial.";
        try {
            JsonNode detail = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(error.getResponseBodyAsString()).get("detail");
            if (detail != null && detail.isTextual()) message = detail.asText();
        } catch (java.io.IOException ignored) { }
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> connectionError(RestClientException error) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "No fue posible conectar con el servicio facial. Intenta nuevamente."));
    }
}
