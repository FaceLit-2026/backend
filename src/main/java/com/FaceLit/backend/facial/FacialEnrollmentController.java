package com.FaceLit.backend.facial;

import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;
import com.FaceLit.backend.facial.service.FacialRegistrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Guided enrollment. Client model scores are NOT a trusted server-side PAD verdict. */
@RestController
@RequestMapping("/api/facial")
public class FacialEnrollmentController {
    private static final double DUPLICATE_FACE_THRESHOLD = 0.65;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PadVerifier pad;
    private final NotificationService notificationService;
    private final FacialRegistrationService registrationService;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Challenge> challenges = new ConcurrentHashMap<>();

    public FacialEnrollmentController(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            PadVerifier pad,
            NotificationService notificationService,
            FacialRegistrationService registrationService) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.pad = pad;
        this.notificationService = notificationService;
        this.registrationService = registrationService;
    }

    public record Challenge(UUID id, List<String> poses, Instant expiresAt) {}
    public record RegistrationStatus(boolean registered, OffsetDateTime registrationDate) {}
    public record Sample(@NotNull String pose, double yaw, double real, double live,
                         @NotNull @Size(min = 1024, max = 1024) List<@NotNull Double> embedding,
                         long elapsedMs, long durationMs, int frames, int blinks, boolean smileTransition) {}
    public record Frame(long elapsedMs, @NotNull @Size(max = 60000) String jpeg) {}
    public record Enrollment(@NotNull UUID challengeId,
                             @NotNull @Size(min = 7, max = 7) List<@NotNull @Valid Sample> samples,
                             @Size(max = 350000) String profilePhoto,
                             @NotNull @Size(min = 21, max = 240) List<@NotNull @Valid Frame> evidence) {
    }

    @GetMapping("/profile-photo")
    public Map<String, String> profilePhoto(@AuthenticationPrincipal UUID userId) {
        var photos = jdbc.queryForList("SELECT profile_photo FROM facialrecognition.user_face WHERE id_user_app = ? AND deleted_at IS NULL AND status = 'ACTIVE' AND profile_photo IS NOT NULL ORDER BY registration_date DESC LIMIT 1", byte[].class, userId);
        return Map.of("photo", photos.isEmpty() ? "" : "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(photos.get(0)));
    }

    static byte[] decodePhoto(String photo) {
        if (photo == null) return null;
        try {
            if (photo.length() > 350000 || !photo.startsWith("data:image/jpeg;base64,")) throw new IllegalArgumentException();
            byte[] bytes = Base64.getDecoder().decode(photo.substring(23));
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if (!reader.getFormatName().equalsIgnoreCase("JPEG") || reader.getWidth(0) != 320 || reader.getHeight(0) != 320) throw new IllegalArgumentException();
                    reader.read(0);
                } finally { reader.dispose(); }
            }
            return bytes;
        } catch (Exception error) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Foto de perfil inválida");
        }
    }

    // The application's generic advice otherwise turns these errors into HTTP 500.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleEnrollmentError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }

    @GetMapping("/me")
    public Map<String, Boolean> status(@AuthenticationPrincipal UUID userId) {
        return Map.of("registered", registered(userId), "padAvailable", pad.available());
    }

    @GetMapping("/registration-status")
    public RegistrationStatus registrationStatus(@AuthenticationPrincipal UUID userId) {
        return registrationStatusForFrontend(userId);
    }

    @PostMapping("/challenge")
    public Challenge challenge(@AuthenticationPrincipal UUID userId, @RequestParam(defaultValue = "false") boolean updatePhoto) {
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
        if (registered(userId) && !updatePhoto) throw new ResponseStatusException(HttpStatus.CONFLICT, "Rostro ya registrado");
        boolean leftFirst = random.nextBoolean();
        var poses = new java.util.ArrayList<>(List.of("center", leftFirst ? "left" : "right", "center", leftFirst ? "right" : "left", "center"));
        poses.add(1 + random.nextInt(poses.size()), random.nextBoolean() ? "blink" : "blink_twice");
        poses.add(1 + random.nextInt(poses.size()), "smile");
        Challenge challenge = new Challenge(UUID.randomUUID(),
            List.copyOf(poses),
            Instant.now().plusSeconds(90));
        challenges.put(userId, challenge);
        return challenge;
    }

    public Challenge challenge(UUID userId) { return challenge(userId, false); }

    @PostMapping("/enrollment")
    public Map<String, Boolean> enroll(@AuthenticationPrincipal UUID userId, @Valid @RequestBody Enrollment request) {
        Challenge challenge = challenges.get(userId);
        if (challenge == null || !challenge.id().equals(request.challengeId())
                || !challenges.remove(userId, challenge) || !challenge.expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Sesión expirada o utilizada");
        }
        validateSamples(challenge, request.samples());
        validateEvidence(request);
        byte[] photo = decodePhoto(request.profilePhoto());
        // Must precede every database mutation, including profile-photo updates.
        pad.requireReal(userId, challenge, request);
        if (!challenge.expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Sesión expirada durante la verificación PAD");
        }
        // Versioned template: magic FLH1 followed by five 1024-float embeddings.
        ByteBuffer template = ByteBuffer.allocate(4 + 5 * 1024 * Float.BYTES);
        template.putInt(0x464c4831);
        request.samples().stream().filter(sample -> List.of("center", "left", "right").contains(sample.pose()))
            .forEach(sample -> sample.embedding().forEach(value -> template.putFloat(value.floatValue())));
        final boolean[] updatedExisting = {false};
        transactions.executeWithoutResult(tx -> {
            // Serialize registrations globally so two different accounts
            // cannot pass the similarity check at the same time.
            jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtext('facialrecognition.user_face.registration'))",
                Long.class);
            // Serialize concurrent registrations for this user using the existing user row.
            jdbc.queryForObject("SELECT id_user_app FROM security.user_app WHERE id_user_app = ? FOR UPDATE", UUID.class, userId);
            ensureUniqueFace(userId, request.samples().get(0).embedding());
            if (registered(userId)) {
                if (photo == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Rostro ya registrado");
                byte[] stored = jdbc.queryForObject("SELECT biometric_vector FROM facialrecognition.user_face WHERE id_user_app = ? AND deleted_at IS NULL AND status = 'ACTIVE' ORDER BY registration_date DESC LIMIT 1", byte[].class, userId);
                if (stored == null || stored.length != 20484) invalid();
                ByteBuffer buffer = ByteBuffer.wrap(stored);
                if (buffer.getInt() != 0x464c4831) invalid();
                double dot = 0, a = 0, b = 0;
                for (double value : request.samples().get(0).embedding()) {
                    double reference = buffer.getFloat();
                    dot += reference * value; a += reference * reference; b += value * value;
                }
                double similarity = dot / Math.sqrt(a * b);
                if (!Double.isFinite(similarity) || similarity < 0.45) invalid();
                jdbc.update("UPDATE facialrecognition.user_face SET profile_photo = ? WHERE id_user_app = ? AND deleted_at IS NULL AND status = 'ACTIVE'", photo, userId);
                updatedExisting[0] = true;
                return;
            }
            jdbc.update("INSERT INTO facialrecognition.user_face (id_user_app, biometric_vector, status, created_by) VALUES (?, ?, 'ACTIVE', ?)",
                userId, template.array(), userId.toString());
            if (photo != null) jdbc.update("UPDATE facialrecognition.user_face SET profile_photo = ? WHERE id_user_app = ? AND deleted_at IS NULL AND status = 'ACTIVE'", photo, userId);
        });
        notifyFacialRegistrationCompleted(userId, updatedExisting[0]);
        return Map.of("registered", true);
    }

    private void ensureUniqueFace(UUID userId, List<Double> embedding) {
        List<Map<String, Object>> candidates = jdbc.queryForList("""
            SELECT biometric_vector
            FROM facialrecognition.user_face
            WHERE id_user_app <> ?
              AND deleted_at IS NULL
              AND status = 'ACTIVE'
            """, userId);

        for (Map<String, Object> candidate : candidates) {
            Object raw = candidate.get("biometric_vector");
            if (!(raw instanceof byte[] stored)) continue;
            double similarity = similarityAgainstTemplate(stored, embedding);
            if (Double.isFinite(similarity) && similarity >= DUPLICATE_FACE_THRESHOLD) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "este rostro ya fue registrado anteriormente en otra cuenta");
            }
        }
    }

    private static double similarityAgainstTemplate(byte[] stored, List<Double> embedding) {
        if (stored.length < 4 + 1024 * Float.BYTES) return Double.NaN;
        ByteBuffer buffer = ByteBuffer.wrap(stored);
        if (buffer.getInt() != 0x464c4831) return Double.NaN;

        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (double value : embedding) {
            double reference = buffer.getFloat();
            dot += reference * value;
            leftNorm += reference * reference;
            rightNorm += value * value;
        }
        if (leftNorm <= 0 || rightNorm <= 0) return Double.NaN;
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    private void notifyFacialRegistrationCompleted(UUID userId, boolean updated) {
        try {
            notificationService.createForRecipient(new CreateNotificationRequestDTO(
                userId,
                "facial_registration_completed",
                updated ? "Tu registro facial fue actualizado" : "Tu registro facial fue completado",
                updated
                    ? "Tu foto de perfil facial fue actualizada correctamente."
                    : "Tu registro facial fue completado correctamente.",
                userId,
                "user_face",
                null,
                "{\"entityType\":\"user_face\",\"entityId\":\"" + userId + "\",\"action\":\""
                    + (updated ? "updated" : "completed") + "\"}"));
        } catch (RuntimeException ignored) {
            // El registro facial no debe fallar por una notificacion informativa.
        }
    }

    @PostMapping("/registration-confirmation")
    public Map<String, Boolean> confirmRegistration(@AuthenticationPrincipal UUID userId) {
        return registrationService.confirm(userId);
    }

    private boolean registered(UUID userId) {
        return registrationService.isRegistered(userId);
    }

    private RegistrationStatus registrationStatusForFrontend(UUID userId) {
        var status = registrationService.status(userId);
        return new RegistrationStatus(status.registered(), status.registrationDate());
    }

    static void validateSamples(Challenge challenge, List<Sample> samples) {
        if (samples == null || samples.size() != challenge.poses().size()) invalid();
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            if (sample == null || !challenge.poses().get(i).equals(sample.pose()) || !matchesPose(sample.pose(), sample.yaw())
                    || !Double.isFinite(sample.real()) || sample.real() < 0.85 || sample.real() > 1
                    || !Double.isFinite(sample.live()) || sample.live() < 0.85 || sample.live() > 1
                    || sample.embedding() == null || sample.embedding().size() != 1024
                    || sample.embedding().stream().anyMatch(v -> v == null || !Double.isFinite(v) || Math.abs(v) > 100)) invalid();
            long previous = i == 0 ? 0 : samples.get(i - 1).elapsedMs();
            if (sample.durationMs() < 650 || sample.durationMs() > 90000 || sample.frames() < 3 || sample.frames() > 240
                    || sample.elapsedMs() < 0 || sample.elapsedMs() > 90000 || sample.elapsedMs() - previous < sample.durationMs()
                    || sample.blinks() != (sample.pose().equals("blink") ? 1 : sample.pose().equals("blink_twice") ? 2 : 0)
                    || (sample.pose().equals("smile") && !sample.smileTransition())) invalid();
            double norm = sample.embedding().stream().mapToDouble(v -> v * v).sum();
            if (norm < 1e-12) invalid();
            if (i > 0) {
                List<Double> reference = samples.get(0).embedding();
                double dot = 0, referenceNorm = 0;
                for (int j = 0; j < 1024; j++) {
                    dot += reference.get(j) * sample.embedding().get(j);
                    referenceNorm += reference.get(j) * reference.get(j);
                }
                if (dot / Math.sqrt(norm * referenceNorm) < 0.45) invalid();
            }
        }
    }

    static boolean matchesPose(String pose, double yaw) {
        if (!Double.isFinite(yaw)) return false;
        return switch (pose) {
            case "center", "blink", "blink_twice", "smile" -> Math.abs(yaw) <= 0.18;
            case "left" -> yaw >= 0.18 && yaw <= 0.85;
            case "right" -> yaw <= -0.18 && yaw >= -0.85;
            default -> false;
        };
    }

    static void validateEvidence(Enrollment request) {
        if (request.evidence() == null || request.evidence().size() < 21 || request.evidence().size() > 240) invalid();
        long previous = -1;
        for (Frame frame : request.evidence()) {
            if (frame == null || frame.elapsedMs() <= previous || frame.elapsedMs() > 90000
                    || frame.jpeg() == null || frame.jpeg().length() > 60000
                    || !frame.jpeg().startsWith("data:image/jpeg;base64,")) invalid();
            previous = frame.elapsedMs();
        }
        for (Sample sample : request.samples()) {
            long count = request.evidence().stream().filter(frame -> frame.elapsedMs() > sample.elapsedMs() - sample.durationMs()
                && frame.elapsedMs() <= sample.elapsedMs()).count();
            if (count < 3) invalid();
        }
    }

    private static void invalid() {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Secuencia facial inválida");
    }
}
