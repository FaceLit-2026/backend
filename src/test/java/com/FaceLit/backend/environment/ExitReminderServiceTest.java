package com.FaceLit.backend.environment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.FaceLit.backend.academic.model.academic.*;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.environment.model.Environment;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.environment.repository.RecordEnvironmentRepository;
import com.FaceLit.backend.environment.service.ExitReminderService;
import com.FaceLit.backend.facial.repository.FacialEventRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.model.*;
import com.FaceLit.backend.notification.repository.*;
import com.FaceLit.backend.notification.service.*;

class ExitReminderServiceTest {
    private final RecordEnvironmentRepository sessions = mock(RecordEnvironmentRepository.class);
    private final FacialEventRepository attendance = mock(FacialEventRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final NotificationService sender = mock(NotificationService.class);
    private final EmailLogRepository logs = mock(EmailLogRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final ExitReminderService service = new ExitReminderService(sessions, attendance, notifications, sender, tx, logs);
    private final RecordEnvironment session = new RecordEnvironment();
    private final OffsetDateTime exit = OffsetDateTime.parse("2026-10-15T18:00:00-05:00");
    private final UUID punctual = UUID.randomUUID(), late = UUID.randomUUID(), configured = UUID.randomUUID(), creator = UUID.randomUUID();

    @BeforeEach void setup() {
        doAnswer(call -> { Consumer<TransactionStatus> action = call.getArgument(0); action.accept(mock(TransactionStatus.class)); return null; })
                .when(tx).executeWithoutResult(any());
        session.setIdRecordEnvironment(UUID.randomUUID()); session.setActive(true);
        session.setEntryTime(exit.minusHours(2)); session.setExitTime(exit); session.setExitReminderSent(false);
        Chip chip = new Chip(); chip.setIdChip(UUID.randomUUID()); chip.setChipCode("3145555"); session.setChip(chip);
        Environment environment = new Environment(); environment.setIdEnvironment(UUID.randomUUID()); environment.setEnvironmentName("Aula 12"); session.setEnvironment(environment);
        Instructor selected = instructor(configured, "María", "López"); session.setInstructorScheduled(selected);
        session.setInstructorInCharge(instructor(creator, "Juan", "Creador"));
        when(sessions.lockForExitReminder(session.getIdRecordEnvironment())).thenReturn(Optional.of(session));
        when(attendance.findExitReminderAttendees(eq(session.getIdRecordEnvironment()), any())).thenReturn(List.of(punctual, late));
    }

    @Test void sendsOnlyToPresentLearnersAndSelectedInstructorAtFiveMinutes() {
        service.queueSession(session.getIdRecordEnvironment(), exit.minusMinutes(5));
        var captured = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(sender, times(3)).createForRecipient(captured.capture());
        assertThat(captured.getAllValues()).extracting(CreateNotificationRequestDTO::recipientUserId)
                .containsExactly(punctual, late, configured).doesNotContain(creator);
        for (var mail : captured.getAllValues()) {
            assertThat(mail.message()).contains("3145555", "María López", "Aula 12", "15/10/2026", "18:00").doesNotContain("Juan Creador");
            assertThat(new NotificationCatalog().require(mail.type()).channel()).isEqualTo("EMAIL");
        }
        assertThat(session.getExitReminderSent()).isTrue();
    }

    @Test void doesNotSendTooEarlyAtExitOrForInactiveDeletedSessions() {
        service.queueSession(session.getIdRecordEnvironment(), exit.minusMinutes(5).minusSeconds(1));
        service.queueSession(session.getIdRecordEnvironment(), exit);
        session.setActive(false); service.queueSession(session.getIdRecordEnvironment(), exit.minusMinutes(4));
        session.setActive(true); session.setDeletedAt(exit.minusDays(1));
        service.queueSession(session.getIdRecordEnvironment(), exit.minusMinutes(4));
        verifyNoInteractions(sender);
    }

    @Test void instructorIsNotifiedEvenWhenNobodyAttended() {
        when(attendance.findExitReminderAttendees(any(), any())).thenReturn(List.of());
        service.queueSession(session.getIdRecordEnvironment(), exit.minusMinutes(5));
        var captured = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(sender).createForRecipient(captured.capture());
        assertThat(captured.getValue().recipientUserId()).isEqualTo(configured);
    }

    @Test void repeatedTicksDoNotDuplicateAndNewArrivalCanStillReceiveReminder() {
        service.queueSession(session.getIdRecordEnvironment(), exit.minusMinutes(5));
        var captured = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(sender, times(3)).createForRecipient(captured.capture());
        when(notifications.findByReferenceEntityAndReferenceId("record_environment", session.getIdRecordEnvironment()))
                .thenReturn(captured.getAllValues().stream().map(this::stored).toList());
        clearInvocations(sender);
        service.queueSession(session.getIdRecordEnvironment(), exit.minusMinutes(4));
        verifyNoInteractions(sender);
        UUID justArrived = UUID.randomUUID();
        when(attendance.findExitReminderAttendees(any(), any())).thenReturn(List.of(punctual, late, justArrived));
        service.queueSession(session.getIdRecordEnvironment(), exit.minusMinutes(3));
        var fresh = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(sender).createForRecipient(fresh.capture());
        assertThat(fresh.getValue().recipientUserId()).isEqualTo(justArrived);
    }

    @Test void changedTimeInvalidatesOldReminderAndQueuesNewSchedule() {
        service.queueSession(session.getIdRecordEnvironment(), exit.minusMinutes(5));
        var captured = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(sender, times(3)).createForRecipient(captured.capture());
        var old = captured.getAllValues().stream().map(this::stored).toList();
        when(notifications.findByReferenceEntityAndReferenceId("record_environment", session.getIdRecordEnvironment())).thenReturn(old);
        session.setExitTime(exit.plusHours(1));
        assertThat(service.stillRelevant(old.get(0), exit.minusMinutes(4))).isFalse();
        clearInvocations(sender);
        service.queueSession(session.getIdRecordEnvironment(), exit.plusHours(1).minusMinutes(5));
        verify(sender, times(3)).createForRecipient(any());
    }

    @Test void absenceCorrectionOrChangedConfiguredInstructorInvalidatesQueuedEmail() {
        service.queueSession(session.getIdRecordEnvironment(), exit.minusMinutes(5));
        var captured = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(sender, times(3)).createForRecipient(captured.capture());
        Notification learner = stored(captured.getAllValues().get(0));
        assertThat(service.stillRelevant(learner, exit.minusMinutes(4))).isTrue();
        when(attendance.findExitReminderAttendees(any(), any())).thenReturn(List.of());
        assertThat(service.stillRelevant(learner, exit.minusMinutes(4))).isFalse();
        Notification teacher = stored(captured.getAllValues().get(2));
        assertThat(service.stillRelevant(teacher, exit.minusMinutes(4))).isTrue();
        session.setInstructorScheduled(instructor(UUID.randomUUID(), "Otro", "Instructor"));
        assertThat(service.stillRelevant(teacher, exit.minusMinutes(4))).isFalse();
    }

    private Notification stored(CreateNotificationRequestDTO request) {
        Notification n = new Notification(); n.setIdNotification(UUID.randomUUID()); n.setIdUserApp(request.recipientUserId());
        n.setType(request.type()); n.setReferenceId(request.referenceId()); n.setMetadataJson(request.metadataJson()); return n;
    }
    private Instructor instructor(UUID id, String first, String last) {
        User user = new User(); user.setIdUser(id); user.setFirstName(first); user.setLastName(last);
        Instructor instructor = new Instructor(); instructor.setIdInstructor(UUID.randomUUID()); instructor.setUser(user); return instructor;
    }
}
