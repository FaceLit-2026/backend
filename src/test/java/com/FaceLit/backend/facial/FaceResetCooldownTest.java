package com.FaceLit.backend.facial;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.FaceLit.backend.academic.model.academic.*;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.facial.model.FaceResetRequest;
import com.FaceLit.backend.facial.repository.FaceResetRequestRepository;
import com.FaceLit.backend.notification.repository.NotificationRepository;
import com.FaceLit.backend.shared.service.UserDisplayNameService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class FaceResetCooldownTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-22T18:00:00Z");
    private final UUID learner = UUID.randomUUID();
    private final FaceResetRequestRepository requests = mock(FaceResetRequestRepository.class);
    private final UserChipRepository assignments = mock(UserChipRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FaceResetService service = new FaceResetService(requests, assignments, notifications, jdbc, 24,
            mock(UserDisplayNameService.class), Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

    @BeforeEach void eligibleLearner() {
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), eq(learner))).thenReturn(true);
        User user = new User(); user.setIdUser(learner); user.setFirstName("Ana"); user.setLastName("Prueba"); user.setDocumentNumber("1000000000");
        Apprentice apprentice = new Apprentice(); apprentice.setUser(user);
        Program program = new Program(); program.setProgramName("Software");
        Chip chip = new Chip(); chip.setChipCode("3145555"); chip.setProgram(program);
        UserChip assignment = new UserChip(); assignment.setApprentice(apprentice); assignment.setChip(chip);
        when(assignments.findByApprentice_User_IdUserAndState(learner, AcademicState.ACTIVE)).thenReturn(Optional.of(assignment));
    }

    private FaceResetRequest last(String status, OffsetDateTime requestedAt) {
        FaceResetRequest last = new FaceResetRequest(); last.setUserId(learner); last.setStatus(status); last.setRequestedAt(requestedAt);
        when(requests.findFirstByUserIdOrderByRequestedAtDesc(learner)).thenReturn(Optional.of(last));
        return last;
    }

    @ParameterizedTest
    @CsvSource({"PENDING,false", "REJECTED,false", "ACCEPTED,false", "ACCEPTED,true"})
    void sevenDaysApplyRegardlessOfDecisionOrCompletedRegistration(String status, boolean consumed) {
        FaceResetRequest last = last(status, NOW.minusDays(1));
        last.setAuthorizationExpiresAt(NOW.minusHours(2));
        if (consumed) last.setConsumedAt(NOW.minusHours(3));
        var timestamp = last.getRequestedAt();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.create(learner)).isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(ex.getReason()).isEqualTo("Ya enviaste una solicitud al coordinador. Podrás enviar una nueva el 28/10/2026 13:00:00 (hora de Colombia).");
            });
        }
        assertThat(last.getRequestedAt()).isEqualTo(timestamp);
        verify(requests, never()).save(any()); verifyNoInteractions(notifications);
        verify(assignments, never()).findByApprentice_User_IdUserAndState(any(), any());
    }

    @Test void evenOneNanosecondBeforeSevenFullDaysIsBlocked() {
        last("REJECTED", NOW.minusDays(7).plusNanos(1));
        assertThatThrownBy(() -> service.create(learner)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(requests, never()).save(any());
    }

    @Test void exactlySevenDaysAllowsNewRequestUsingTheSameInstantAcrossOffsets() {
        last("REJECTED", NOW.minusDays(7).withOffsetSameInstant(ZoneOffset.ofHours(-5)));
        assertThat(service.create(learner).status()).isEqualTo("PENDING");
        var capture = org.mockito.ArgumentCaptor.forClass(FaceResetRequest.class);
        verify(requests).save(capture.capture());
        assertThat(capture.getValue().getRequestedAt()).isEqualTo(NOW);
    }

    @Test void firstSuccessfulRequestStartsCooldownAndRepeatedClicksDoNotMoveIt() {
        var stored = new AtomicReference<FaceResetRequest>();
        when(requests.findFirstByUserIdOrderByRequestedAtDesc(learner)).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(requests.save(any())).thenAnswer(call -> { stored.set(call.getArgument(0)); return stored.get(); });
        assertThat(service.create(learner).status()).isEqualTo("PENDING");
        assertThatThrownBy(() -> service.create(learner)).hasMessageContaining("429");
        assertThat(stored.get().getRequestedAt()).isEqualTo(NOW);
        verify(requests, times(1)).save(any()); verifyNoInteractions(notifications);
    }

    @Test void unsuccessfulAttemptDoesNotStartOrChangeCooldown() {
        var previous = last("REJECTED", NOW.minusDays(8));
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), eq(learner))).thenReturn(false);
        assertThatThrownBy(() -> service.create(learner)).hasMessageContaining("registro facial previo");
        assertThat(previous.getRequestedAt()).isEqualTo(NOW.minusDays(8));
        verify(requests, never()).save(any());
    }

    @Test void existingPendingProtectionRemainsAfterSevenDays() {
        last("PENDING", NOW.minusDays(8));
        assertThatThrownBy(() -> service.create(learner)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(requests, never()).save(any());
    }

    @Test void controllerExposesAvailabilityMessageToTheExistingFrontend() {
        last("REJECTED", NOW.minusDays(1));
        var failure = catchThrowableOfType(() -> service.create(learner), ResponseStatusException.class);
        var response = new FaceResetController(service).error(failure);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).containsEntry("message",
                "Ya enviaste una solicitud al coordinador. Podrás enviar una nueva el 28/10/2026 13:00:00 (hora de Colombia).");
        verify(requests, never()).save(any());
    }
}
