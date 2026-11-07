package com.FaceLit.backend.facial;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class FacialEmbeddingVerificationClient {

    private final String apiKey;
    private final RestClient client;

    public FacialEmbeddingVerificationClient(
            @Value("${facial.embedding.url:http://localhost:8090}") String baseUrl,
            @Value("${facial.embedding.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank() || "change-me".equals(apiKey.trim())) {
            throw new IllegalArgumentException("Configura FACIAL_EMBEDDING_API_KEY con una clave de servicio propia.");
        }
        this.apiKey = apiKey;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(180));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public VerificationResponse verifySession(UUID idRecordEnvironment, String imageBase64, List<String> imageFrames, String livenessChallenge, List<String> livenessChallenges) {
        return client.post()
                .uri("/api/v1/facial-embeddings/verify-session")
                .header("X-API-Key", apiKey)
                .body(new VerificationRequest(idRecordEnvironment, imageBase64, imageFrames, livenessChallenge, livenessChallenges))
                .retrieve()
                .body(VerificationResponse.class);
    }

    public JsonNode registerFromImage(Object payload) {
        return client.post().uri("/api/v1/facial-embeddings/from-image")
                .header("X-API-Key", apiKey).body(payload).retrieve().body(JsonNode.class);
    }

    public JsonNode checkLiveness(Object payload) {
        return client.post().uri("/api/v1/facial-embeddings/liveness-check")
                .header("X-API-Key", apiKey).body(payload).retrieve().body(JsonNode.class);
    }

    record VerificationRequest(
            @JsonProperty("record_environment_id") UUID recordEnvironmentId,
            @JsonProperty("image_base64") String imageBase64,
            @JsonProperty("image_frames") List<String> imageFrames,
            @JsonProperty("liveness_challenge") String livenessChallenge,
            @JsonProperty("liveness_challenges") List<String> livenessChallenges) {
    }

    public record VerificationResponse(
            boolean match,
            boolean live,
            @JsonProperty("id_apprentice") UUID idApprentice,
            BigDecimal similarity,
            BigDecimal threshold,
            @JsonProperty("model_name") String modelName,
            String reason,
            @JsonProperty("liveness_reason") String livenessReason) {
    }
}
