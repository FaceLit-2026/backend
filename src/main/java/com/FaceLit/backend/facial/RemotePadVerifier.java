package com.FaceLit.backend.facial;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Server-to-server adapter contract. No fallback to browser-provided scores. */
@Service
public class RemotePadVerifier implements PadVerifier {
    private final String url;
    private final String token;
    private final RestClient client;

    public RemotePadVerifier(@Value("${facial.pad.url:}") String url,
                             @Value("${facial.pad.token:}") String token) {
        this.url = url;
        this.token = token;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean available() {
        if (url.isBlank() || token.isBlank()) return false;
        try { return "https".equals(URI.create(url).getScheme()); }
        catch (IllegalArgumentException error) { return false; }
    }

    record Verification(UUID userId, FacialEnrollmentController.Challenge challenge,
                        FacialEnrollmentController.Enrollment enrollment) {}
    record Verdict(UUID userId, UUID challengeId, String liveness, boolean depth,
                   boolean textureAndReflection, boolean activeChallenge, boolean temporal,
                   boolean faceAntiSpoof, boolean identityAndEmbeddingMatch) {}

    public void requireReal(UUID user, FacialEnrollmentController.Challenge challenge,
                            FacialEnrollmentController.Enrollment enrollment) {
        if (!available()) throw unavailable();
        Verdict verdict;
        try {
            verdict = client.post().uri(url).headers(h -> h.setBearerAuth(token))
                .body(new Verification(user, challenge, enrollment)).retrieve().body(Verdict.class);
        } catch (Exception error) { throw unavailable(); }
        if (!accepts(verdict, user, challenge.id())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "PAD no confirmó una persona REAL. No se guardó el rostro.");
        }
    }

    static boolean accepts(Verdict v, UUID user, UUID challenge) {
        return v != null && user.equals(v.userId()) && challenge.equals(v.challengeId())
            && "REAL".equals(v.liveness()) && v.depth() && v.textureAndReflection()
            && v.activeChallenge() && v.temporal() && v.faceAntiSpoof() && v.identityAndEmbeddingMatch();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Verificación PAD no disponible. El registro facial está bloqueado.");
    }
}
