package com.FaceLit.backend.notification.service.serviceImpl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.FaceLit.backend.auth.model.security.Credential;
import com.FaceLit.backend.auth.repository.security.CredentialRepository;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.environment.service.ExitReminderService;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.model.*;
import com.FaceLit.backend.notification.repository.*;
import com.FaceLit.backend.notification.service.*;
import com.FaceLit.backend.shared.service.EmailService;
import org.mockito.ArgumentCaptor;

class ExitReminderEmailTest {
    private final EmailLogRepository logs = mock(EmailLogRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final CredentialRepository credentials = mock(CredentialRepository.class);
    private final EmailService mail = mock(EmailService.class);
    private final ExitReminderService reminders = mock(ExitReminderService.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final EmailDispatchServiceImpl dispatcher = new EmailDispatchServiceImpl(logs, notifications, credentials, mail, reminders, tx);
    private final Notification notification = new Notification();
    private final EmailLog email = new EmailLog();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach void setup() {
        notification.setIdNotification(UUID.randomUUID()); notification.setIdUserApp(userId);
        notification.setType("attendance_exit_reminder"); notification.setTitle("Recordatorio de salida");
        notification.setChannel("EMAIL"); notification.setMessage("Ficha 3145555, salida 18:00, instructora María López");
        email.setIdNotification(notification.getIdNotification());
        when(notifications.findById(notification.getIdNotification())).thenReturn(Optional.of(notification));
        when(logs.lockByNotification(notification.getIdNotification())).thenReturn(Optional.of(email));
        Credential credential = new Credential(); credential.setEmail("learner@example.test");
        when(credentials.findByUser_IdUser(userId)).thenReturn(Optional.of(credential));
        when(reminders.stillRelevant(eq(notification), any())).thenReturn(true);
        doAnswer(call -> { Consumer<TransactionStatus> action = call.getArgument(0); action.accept(mock(TransactionStatus.class)); return null; })
                .when(tx).executeWithoutResult(any());
    }

    @Test void validEmailIsSentOnceAndRecorded() {
        dispatcher.onEmailDispatchRequested(new EmailDispatchRequested(notification.getIdNotification()));
        dispatcher.onEmailDispatchRequested(new EmailDispatchRequested(notification.getIdNotification()));
        verify(mail).sendNotificationEmail("learner@example.test", "FaceLit - Recordatorio de salida", notification.getMessage());
        assertThat(email.getSendStatus()).isEqualTo(EmailLog.SendStatus.SENT);
    }

    @Test void obsoleteReminderIsCancelledWithoutSending() {
        when(reminders.stillRelevant(eq(notification), any())).thenReturn(false);
        dispatcher.sendAndLog(notification.getIdNotification());
        assertThat(email.getSendStatus()).isEqualTo(EmailLog.SendStatus.CANCELLED);
        verifyNoInteractions(mail);
        verify(logs).save(email);
    }

    @Test void failedReminderIsRetriedWithinTheWindow() {
        doThrow(new IllegalStateException("SMTP unavailable")).doNothing().when(mail).sendNotificationEmail(anyString(), anyString(), anyString());
        dispatcher.sendAndLog(notification.getIdNotification());
        assertThat(email.getSendStatus()).isEqualTo(EmailLog.SendStatus.FAILED);
        when(logs.findExitRemindersToRetry(any())).thenReturn(List.of(email));
        dispatcher.retryExitReminders();
        assertThat(email.getSendStatus()).isEqualTo(EmailLog.SendStatus.SENT);
        assertThat(email.getAttempts()).isEqualTo(1);
    }

    @Test void emailOnlyNotificationsAreExcludedFromAppInboxAndMarkAllRead() {
        var service = new NotificationServiceImpl(notifications, logs, mock(UserRoleRepository.class), new NotificationCatalog(), mock(ApplicationEventPublisher.class), mock(com.FaceLit.backend.shared.service.UserDisplayNameService.class));
        Notification app = new Notification(); app.setIdNotification(UUID.randomUUID()); app.setIdUserApp(userId); app.setType("attendance_late"); app.setChannel("APP_EMAIL");
        when(notifications.findByIdUserAppAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(List.of(notification, app));
        assertThat(service.findForUser(userId)).extracting(n -> n.type()).containsExactly("attendance_late");
        when(notifications.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        service.markAllRead(userId);
        assertThat(app.isReadStatus()).isTrue();
        assertThat(notification.isReadStatus()).isFalse();
    }

    @Test void emailOnlyCreationPersistsEmailOutboxAndPublishesDispatch() {
        var publisher = mock(ApplicationEventPublisher.class);
        var service = new NotificationServiceImpl(notifications, logs, mock(UserRoleRepository.class), new NotificationCatalog(), publisher, mock(com.FaceLit.backend.shared.service.UserDisplayNameService.class));
        when(notifications.save(any())).thenAnswer(call -> { Notification n = call.getArgument(0); n.setIdNotification(UUID.randomUUID()); return n; });
        service.createForRecipient(new CreateNotificationRequestDTO(userId, "attendance_exit_reminder", "Salida", "Registra tu salida", UUID.randomUUID(), "record_environment", null, "{}"));
        var captured = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captured.capture());
        assertThat(captured.getValue().getChannel()).isEqualTo("EMAIL");
        verify(logs).save(any(EmailLog.class));
        verify(publisher).publishEvent(any(EmailDispatchRequested.class));
    }

    @Test void oldFacialDecisionReturnsCoordinatorNameWithoutRewritingAuditMetadata() {
        var names = mock(com.FaceLit.backend.shared.service.UserDisplayNameService.class);
        UUID coordinator = UUID.randomUUID();
        when(names.fullName(coordinator)).thenReturn("Maria Rodriguez");
        var service = new NotificationServiceImpl(notifications, logs, mock(UserRoleRepository.class),
                new NotificationCatalog(), mock(ApplicationEventPublisher.class), names);
        notification.setType("facial_request_resolved"); notification.setChannel("APP_EMAIL");
        String original = "{\"decidedBy\":\"" + coordinator + "\",\"facialDecision\":\"accepted\"}";
        notification.setMetadataJson(original);
        when(notifications.findByIdUserAppAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(List.of(notification));
        assertThat(service.findForUser(userId).get(0).meta()).contains("Maria Rodriguez", coordinator.toString());
        assertThat(notification.getMetadataJson()).isEqualTo(original);
        verify(notifications, never()).save(any());
    }
}
