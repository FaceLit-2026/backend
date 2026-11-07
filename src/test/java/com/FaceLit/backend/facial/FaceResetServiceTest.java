package com.FaceLit.backend.facial;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.FaceLit.backend.academic.model.academic.*;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.UserChipRepository;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.roleandpermission.UserRole;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.facial.model.FaceResetRequest;
import com.FaceLit.backend.facial.repository.FaceResetRequestRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.model.Notification;
import com.FaceLit.backend.notification.repository.NotificationRepository;
import com.FaceLit.backend.notification.service.NotificationCatalog;
import com.FaceLit.backend.notification.service.NotificationService;
import org.mockito.ArgumentCaptor;

class FaceResetServiceTest {
    @Test void unusedExpiredAuthorizationNotifiesOnlyTheLearnerInApp() {
        service.resolve(notification.getIdNotification(), coordinatorId, "accepted");
        request.setRequestNotified(true); request.setDecisionNotified(true);
        request.setAuthorizationExpiresAt(OffsetDateTime.now().minusMinutes(1));
        delivery.deliver(request.getId());
        var capture = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(notificationService).createForRecipient(capture.capture());
        assertThat(capture.getValue().type()).isEqualTo("facial_authorization_expired");
        assertThat(capture.getValue().metadataJson()).contains("recipientName", "learner", "authorizationExpiresAt");
        assertThat(capture.getValue().message()).contains("cámara permanece bloqueada", "rostro anterior se conserva");
        assertThat(capture.getValue().recipientUserId()).isEqualTo(learnerId);
        assertThat(new NotificationCatalog().require(capture.getValue().type()).channel()).isEqualTo("APP");
        request.setConsumedAt(OffsetDateTime.now()); request.setCompletionNotified(true);
        clearInvocations(notificationService); delivery.deliver(request.getId()); verifyNoInteractions(notificationService);
    }
    private final UUID learnerId = UUID.randomUUID(), coordinatorId = UUID.randomUUID();
    private final FaceResetRequestRepository requests = mock(FaceResetRequestRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserChipRepository assignments = mock(UserChipRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final com.FaceLit.backend.shared.service.UserDisplayNameService names = mock(com.FaceLit.backend.shared.service.UserDisplayNameService.class);
    private final FaceResetService service = new FaceResetService(requests, assignments, notifications, jdbc, 24, names);
    private final FaceResetRequest request = new FaceResetRequest();
    private final Notification notification = new Notification();
    private FaceResetNotificationDelivery delivery;

    @BeforeEach void setup() {
        when(names.fullName(coordinatorId)).thenReturn("Maria Rodriguez");
        request.setUserId(learnerId);
        request.setContextJson(service.writeJson(new LinkedHashMap<>(Map.of("learnerName", "Mariana López",
                "fichaNumber", "3145555", "programName", "Análisis de software", "requestId", request.getId().toString()))));
        notification.setIdNotification(UUID.randomUUID());
        notification.setIdUserApp(coordinatorId);
        notification.setType("facial_reregister_request");
        notification.setReferenceEntity("face_reset_request");
        notification.setReferenceId(request.getId());
        when(notifications.findById(notification.getIdNotification())).thenReturn(Optional.of(notification));
        when(notifications.findByReferenceEntityAndReferenceId("face_reset_request", request.getId())).thenReturn(List.of(notification));
        when(requests.lockById(request.getId())).thenReturn(Optional.of(request));
        when(requests.findFirstByUserIdOrderByRequestedAtDesc(learnerId)).thenReturn(Optional.of(request));
        var roles = mock(UserRoleRepository.class);
        var role = mock(UserRole.class);
        User coordinator = new User(); coordinator.setIdUser(coordinatorId);
        when(role.getUser()).thenReturn(coordinator);
        when(roles.findByRoleName(RoleName.COORDINATOR)).thenReturn(List.of(role));
        var tx = mock(TransactionTemplate.class);
        doAnswer(call -> { Consumer<TransactionStatus> action = call.getArgument(0); action.accept(mock(TransactionStatus.class)); return null; })
                .when(tx).executeWithoutResult(any());
        delivery = new FaceResetNotificationDelivery(requests, notifications, roles, notificationService, service, tx, "https://facelit.example");
    }

    @Test void approvalCreatesSingleUseGrantAndAuditedDecision() {
        assertThat(service.current(learnerId).canReregister()).isFalse();
        service.resolve(notification.getIdNotification(), coordinatorId, "accepted");
        assertThat(request.getResolvedBy()).isEqualTo(coordinatorId);
        assertThat(request.getResolvedAt()).isNotNull();
        assertThat(request.getAuthorizationExpiresAt()).isEqualTo(request.getResolvedAt().plusHours(24));
        assertThat(service.current(learnerId).authorizationToken()).isNotNull();
        assertThat(service.current(learnerId).canReregister()).isTrue();
        assertThat(notification.getMetadataJson()).contains("accepted", coordinatorId.toString(), "Maria Rodriguez");
        assertThatThrownBy(() -> service.resolve(notification.getIdNotification(), coordinatorId, "accepted"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        request.setConsumedAt(OffsetDateTime.now());
        assertThat(service.current(learnerId).canReregister()).isFalse();
        assertThat(service.current(learnerId).authorizationToken()).isNull();
    }

    @Test void expiredAuthorizationCannotEnableReregistration() {
        request.setStatus("ACCEPTED");
        request.setAuthorizationToken(UUID.randomUUID());
        request.setAuthorizationExpiresAt(OffsetDateTime.now().minusSeconds(1));
        assertThat(service.current(learnerId).canReregister()).isFalse();
        assertThat(service.current(learnerId).authorizationToken()).isNull();
    }

    @Test void anotherRecipientCannotResolve() {
        assertThatThrownBy(() -> service.resolve(notification.getIdNotification(), UUID.randomUUID(), "accepted"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThat(request.getStatus()).isEqualTo("PENDING");
        verify(requests, never()).lockById(any());
    }

    @Test void apprenticeCannotResolveThroughNotificationEndpoint() {
        var controller = new com.FaceLit.backend.notification.controller.NotificationController(notificationService, service);
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                learnerId, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_APPRENTICE")));
        assertThatThrownBy(() -> controller.resolveFacialRequest(notification.getIdNotification(),
                new com.FaceLit.backend.notification.dto.request.FacialDecisionRequestDTO("accepted"), authentication))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThat(request.getStatus()).isEqualTo("PENDING");
        verify(requests, never()).lockById(any());
    }

    @Test void pendingRequestCannotBeDuplicated() {
        assertThatThrownBy(() -> service.create(learnerId)).hasMessageContaining("429");
        verify(requests, never()).save(any());
    }

    @Test void rejectionAllowsNewRequestAfterSevenDaysWithAcademicContext() {
        service.resolve(notification.getIdNotification(), coordinatorId, "rejected");
        assertThat(service.current(learnerId).canReregister()).isFalse();
        assertThat(request.getAuthorizationToken()).isNull();
        request.setRequestedAt(OffsetDateTime.now().minusDays(8));
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(learnerId))).thenReturn(true);
        User learner = new User(); learner.setIdUser(learnerId); learner.setFirstName("Mariana"); learner.setLastName("López");
        Apprentice apprentice = new Apprentice(); apprentice.setUser(learner);
        Program program = new Program(); program.setProgramName("Análisis de software");
        Chip chip = new Chip(); chip.setChipCode("3145555"); chip.setProgram(program);
        UserChip assignment = new UserChip(); assignment.setChip(chip); assignment.setApprentice(apprentice);
        when(assignments.findByApprentice_User_IdUserAndState(learnerId, AcademicState.ACTIVE)).thenReturn(Optional.of(assignment));
        assertThat(service.create(learnerId).status()).isEqualTo("PENDING");
        var captured = ArgumentCaptor.forClass(FaceResetRequest.class);
        verify(requests, times(2)).save(captured.capture());
        assertThat(captured.getValue().getContextJson()).contains("Mariana López", "3145555", "Análisis de software");
    }

    @Test void coordinatorRequestContainsLearnerChipProgramAndAppLink() {
        when(notifications.findByReferenceEntityAndReferenceId("face_reset_request", request.getId())).thenReturn(List.of());
        delivery.deliver(request.getId());
        var captured = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(notificationService).createForRecipient(captured.capture());
        var sent = captured.getValue();
        assertThat(sent.recipientUserId()).isEqualTo(coordinatorId);
        assertThat(sent.message()).contains("Mariana López", "3145555", "Análisis de software", "https://facelit.example/notifications");
        assertThat(new NotificationCatalog().require(sent.type()).channel()).isEqualTo("APP_EMAIL");
        assertThat(sent.metadataJson()).doesNotContain("authorizationToken");
    }

    @Test void failedDeliveryLeavesDecisionAndAuthorizationReadyForRetry() {
        service.resolve(notification.getIdNotification(), coordinatorId, "accepted");
        request.setRequestNotified(true);
        when(notificationService.createForRecipient(any())).thenThrow(new IllegalStateException("delivery unavailable"));
        assertThatThrownBy(() -> delivery.deliver(request.getId())).hasMessageContaining("delivery unavailable");
        assertThat(request.getStatus()).isEqualTo("ACCEPTED");
        assertThat(request.canRegister()).isTrue();
        assertThat(request.isDecisionNotified()).isFalse();
        reset(notificationService);
        delivery.deliver(request.getId());
        assertThat(request.isDecisionNotified()).isTrue();
        var captured = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(notificationService).createForRecipient(captured.capture());
        assertThat(captured.getValue().recipientUserId()).isEqualTo(learnerId);
        assertThat(captured.getValue().message()).contains("Decidido por: Maria Rodriguez").doesNotContain(coordinatorId.toString());
        assertThat(captured.getValue().metadataJson()).contains("accepted").doesNotContain("authorizationToken");
    }
}
