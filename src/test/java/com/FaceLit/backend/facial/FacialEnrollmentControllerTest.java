package com.FaceLit.backend.facial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.FaceLit.backend.notification.service.NotificationService;

class FacialEnrollmentControllerTest {
    @Test void confirmationRequiresPersistedFaceAndDoesNotDuplicate() {
        doAnswer(call -> { Consumer<TransactionStatus> action = call.getArgument(0); action.accept(mock(TransactionStatus.class)); return null; }).when(tx).executeWithoutResult(any());
        when(jdbc.queryForObject(contains("JOIN facialrecognition.user_face"), eq(Boolean.class), eq(user))).thenReturn(true);
        controller.confirmRegistration(user);
        verify(notificationService).createForRecipient(argThat(n -> n.type().equals("facial_registration_completed") && n.recipientUserId().equals(user)));
        when(jdbc.queryForObject(contains("FROM notification.notification"), eq(Boolean.class), eq(user))).thenReturn(true);
        controller.confirmRegistration(user);
        verifyNoMoreInteractions(notificationService);
        when(jdbc.queryForObject(contains("JOIN facialrecognition.user_face"), eq(Boolean.class), eq(user))).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> controller.confirmRegistration(user));
    }
    private String photo() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(320, 320, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", output);
        return "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(output.toByteArray());
    }

    @Test void validatesActualPhotoContent() throws Exception {
        assertNotNull(FacialEnrollmentController.decodePhoto(photo()));
        assertThrows(ResponseStatusException.class, () -> FacialEnrollmentController.decodePhoto("data:image/jpeg;base64,aGVsbG8="));
        assertThrows(ResponseStatusException.class, () -> FacialEnrollmentController.decodePhoto("data:image/png;base64,aGVsbG8="));
    }

    @Test void existingFacePhotoMustMatchStoredIdentity() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(user))).thenReturn(true);
        doAnswer(call -> {
            Consumer<TransactionStatus> action = call.getArgument(0);
            action.accept(mock(TransactionStatus.class)); return null;
        }).when(tx).executeWithoutResult(any());
        var stored = java.nio.ByteBuffer.allocate(20484).putInt(0x464c4831);
        for (int i = 0; i < 5120; i++) stored.putFloat(-0.1f);
        when(jdbc.queryForObject(anyString(), eq(byte[].class), eq(user))).thenReturn(stored.array());
        var challenge = controller.challenge(user, true);
        var request = enrollment(challenge, samples(challenge), photo());
        assertThrows(ResponseStatusException.class, () -> controller.enroll(user, request));
        verify(jdbc, never()).update(contains("UPDATE"), any(byte[].class), eq(user));
        stored.position(4);
        for (int i = 0; i < 5120; i++) stored.putFloat(0.1f);
        challenge = controller.challenge(user, true);
        assertTrue(controller.enroll(user, enrollment(challenge, samples(challenge), photo())).get("registered"));
        verify(jdbc).update(contains("UPDATE"), any(byte[].class), eq(user));
        verify(jdbc, never()).update(contains("INSERT"), eq(user), any(byte[].class), anyString());
    }

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final PadVerifier pad = mock(PadVerifier.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final FacialEnrollmentController controller = new FacialEnrollmentController(jdbc, tx, pad, notificationService,
            new com.FaceLit.backend.facial.service.FacialRegistrationService(
                    new com.FaceLit.backend.facial.repository.FacialRegistrationStatusRepository(jdbc), tx, notificationService));
    private final UUID user = UUID.randomUUID();

    private List<FacialEnrollmentController.Sample> samples(FacialEnrollmentController.Challenge challenge) {
        var result = new ArrayList<FacialEnrollmentController.Sample>();
        for (String pose : challenge.poses()) result.add(new FacialEnrollmentController.Sample(
            pose, pose.equals("left") ? 0.4 : pose.equals("right") ? -0.4 : 0,
            0.95, 0.95, Collections.nCopies(1024, 0.1), (result.size() + 1) * 1000L, 800, 4,
            pose.equals("blink") ? 1 : pose.equals("blink_twice") ? 2 : 0, pose.equals("smile")));
        return result;
    }

    private FacialEnrollmentController.Enrollment enrollment(FacialEnrollmentController.Challenge challenge,
            List<FacialEnrollmentController.Sample> samples, String photo) {
        var evidence = new ArrayList<FacialEnrollmentController.Frame>();
        for (long time = 0; time <= 7000; time += 200)
            evidence.add(new FacialEnrollmentController.Frame(time, "data:image/jpeg;base64,test"));
        return new FacialEnrollmentController.Enrollment(challenge.id(), samples, photo, evidence);
    }

    @Test void acceptsCompleteSequenceAndPersistsOnlyOnce() {
        doAnswer(call -> {
            Consumer<TransactionStatus> action = call.getArgument(0);
            action.accept(mock(TransactionStatus.class)); return null;
        }).when(tx).executeWithoutResult(any());
        var challenge = controller.challenge(user);
        var request = enrollment(challenge, samples(challenge), null);
        assertTrue(controller.enroll(user, request).get("registered"));
        verify(pad).requireReal(user, challenge, request);
        verify(jdbc).update(contains("INSERT INTO facialrecognition.user_face"), eq(user),
            argThat((byte[] template) -> template.length == 20484), eq(user.toString()));
        assertThrows(ResponseStatusException.class, () -> controller.enroll(user, request));
    }

    @Test void challengeCannotBeUsedByAnotherUser() {
        var challenge = controller.challenge(user);
        assertThrows(ResponseStatusException.class, () -> controller.enroll(UUID.randomUUID(),
            enrollment(challenge, samples(challenge), null)));
        verifyNoInteractions(tx);
    }

    @Test void newChallengeInvalidatesOldChallenge() {
        var old = controller.challenge(user);
        controller.challenge(user);
        assertThrows(ResponseStatusException.class, () -> controller.enroll(user,
            enrollment(old, samples(old), null)));
        verifyNoInteractions(tx);
    }

    @Test void rejectsStationaryPhotoDespiteHighScores() {
        var challenge = controller.challenge(user);
        var still = samples(challenge).stream().map(s -> new FacialEnrollmentController.Sample(s.pose(), 0, s.real(), s.live(), s.embedding(), s.elapsedMs(), s.durationMs(), s.frames(), s.blinks(), s.smileTransition())).toList();
        assertThrows(ResponseStatusException.class, () -> controller.enroll(user,
            enrollment(challenge, still, null)));
        verifyNoInteractions(tx);
    }

    @Test void rejectsSpoofScoresAndConsumesChallenge() {
        var challenge = controller.challenge(user);
        var samples = samples(challenge);
        samples.set(0, new FacialEnrollmentController.Sample("center", 0, 0.3, 0.95, samples.get(0).embedding(), 1000, 800, 4, 0, false));
        var request = enrollment(challenge, samples, null);
        assertThrows(ResponseStatusException.class, () -> controller.enroll(user, request));
        assertThrows(ResponseStatusException.class, () -> controller.enroll(user, request));
        verifyNoInteractions(tx);
    }

    @Test void rejectsInvalidOrDifferentIdentityEmbedding() {
        var challenge = controller.challenge(user);
        for (double value : new double[] { 0, Double.NaN, Double.POSITIVE_INFINITY, -0.1 }) {
            var samples = samples(challenge);
            var previous = samples.get(1);
            samples.set(1, new FacialEnrollmentController.Sample(previous.pose(), previous.yaw(), 0.95, 0.95,
                Collections.nCopies(1024, value), previous.elapsedMs(), previous.durationMs(), previous.frames(), previous.blinks(), previous.smileTransition()));
            assertThrows(ResponseStatusException.class, () -> FacialEnrollmentController.validateSamples(challenge, samples));
        }
    }

    @Test void rejectsMissingOrOutOfOrderSteps() {
        var challenge = new FacialEnrollmentController.Challenge(UUID.randomUUID(),
            List.of("center", "left", "center", "right", "center"), Instant.now().plusSeconds(90));
        var samples = samples(challenge);
        Collections.swap(samples, 1, 3);
        assertThrows(ResponseStatusException.class, () -> FacialEnrollmentController.validateSamples(challenge, samples));
        samples.remove(0);
        assertThrows(ResponseStatusException.class, () -> FacialEnrollmentController.validateSamples(challenge, samples));
    }

    @Test void existingRegistrationCannotBeOverwritten() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(user))).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> controller.challenge(user));
        verifyNoInteractions(tx);
    }

    @Test void rejectsFaceAlreadyRegisteredInAnotherAccount() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(user))).thenReturn(false);
        doAnswer(call -> {
            Consumer<TransactionStatus> action = call.getArgument(0);
            action.accept(mock(TransactionStatus.class)); return null;
        }).when(tx).executeWithoutResult(any());

        var stored = java.nio.ByteBuffer.allocate(20484).putInt(0x464c4831);
        for (int i = 0; i < 5120; i++) stored.putFloat(0.1f);
        when(jdbc.queryForList(anyString(), eq(user)))
            .thenReturn(List.of(Map.of("biometric_vector", stored.array())));

        var challenge = controller.challenge(user, true);
        var request = enrollment(challenge, samples(challenge), null);
        var error = assertThrows(ResponseStatusException.class, () -> controller.enroll(user, request));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("este rostro ya fue registrado anteriormente en otra cuenta", error.getReason());
        verify(jdbc, never()).update(contains("INSERT"), eq(user), any(byte[].class), anyString());
    }

    @Test void padFailureBlocksBothEnrollmentAndPhotoUpdates() throws Exception {
        for (boolean registered : new boolean[] { false, true }) {
            when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(user))).thenReturn(registered);
            var challenge = controller.challenge(user, registered);
            var request = enrollment(challenge, samples(challenge), registered ? photo() : null);
            doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
                .when(pad).requireReal(user, challenge, request);
            assertThrows(ResponseStatusException.class, () -> controller.enroll(user, request));
            verifyNoInteractions(tx);
            assertThrows(ResponseStatusException.class, () -> controller.enroll(user, request));
        }
    }

    @Test void rejectsMissingEvidenceAndInstantGestures() {
        var challenge = controller.challenge(user);
        var samples = samples(challenge);
        var request = new FacialEnrollmentController.Enrollment(challenge.id(), samples, null, List.of());
        assertThrows(ResponseStatusException.class, () -> controller.enroll(user, request));
        var first = samples.get(0);
        samples.set(0, new FacialEnrollmentController.Sample(first.pose(), first.yaw(), 1, 1, first.embedding(), 1000, 0, 1, 0, false));
        assertThrows(ResponseStatusException.class, () -> FacialEnrollmentController.validateSamples(challenge, samples));
        verifyNoInteractions(pad, tx);
    }

    @Test void randomChallengesContainRequiredActionsAndFiveTemplatePoses() {
        var sequences = new java.util.HashSet<List<String>>();
        for (int i = 0; i < 100; i++) {
            var poses = controller.challenge(user).poses();
            assertEquals(7, poses.size());
            assertEquals(3, Collections.frequency(poses, "center"));
            assertTrue(poses.containsAll(List.of("left", "right", "smile")));
            assertTrue(poses.contains("blink") ^ poses.contains("blink_twice"));
            sequences.add(poses);
        }
        assertTrue(sequences.size() > 2);
    }
}
