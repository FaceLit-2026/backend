package com.FaceLit.backend.facial;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;

@Service
public class FacialEmbeddingRegistrationService {
    private final FacialEmbeddingVerificationClient client;

    public FacialEmbeddingRegistrationService(FacialEmbeddingVerificationClient client) {
        this.client = client;
    }

    public record Registration(
            @JsonProperty("user_id") @NotNull UUID userId,
            @JsonProperty("image_base64") @NotBlank @Size(max = 5000000) String imageBase64,
            @JsonProperty("image_frames") @Size(max = 18) List<@NotBlank @Size(max = 5000000) String> imageFrames,
            @JsonProperty("liveness_challenge") @Size(max = 40) String livenessChallenge,
            @JsonProperty("liveness_challenges") @Size(max = 3) List<@NotBlank @Size(max = 40) String> livenessChallenges,
            @JsonProperty("photo_reference") @Size(max = 500) String photoReference,
            @JsonProperty("replace_existing") boolean replaceExisting,
            @JsonProperty("reset_token") UUID resetToken) {}

    public JsonNode register(Registration request, UUID userId) {
        if (!request.userId().equals(userId)) {
            throw new OwnershipException();
        }
        // Identity and audit author come from the validated session, never from a client claim.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("created_by", userId.toString());
        payload.put("image_base64", request.imageBase64());
        payload.put("image_frames", request.imageFrames() == null ? List.of() : request.imageFrames());
        payload.put("liveness_challenge", request.livenessChallenge());
        payload.put("liveness_challenges", request.livenessChallenges() == null ? List.of() : request.livenessChallenges());
        payload.put("photo_reference", request.photoReference());
        payload.put("replace_existing", request.replaceExisting());
        payload.put("reset_token", request.resetToken());
        JsonNode response = client.registerFromImage(payload);
        if (response instanceof ObjectNode object) object.remove("embedding");
        return response;
    }

    static final class OwnershipException extends RuntimeException {
        OwnershipException() {
            super("Solo puedes registrar tu propio rostro.");
        }
    }
}
