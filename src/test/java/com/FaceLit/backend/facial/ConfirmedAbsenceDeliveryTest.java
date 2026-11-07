package com.FaceLit.backend.facial;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.FaceLit.backend.facial.repository.FacialEventRepository;
import com.FaceLit.backend.facial.model.FacialEvent;
import com.FaceLit.backend.facial.model.enums.*;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.academic.model.academic.Apprentice;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.facial.service.serviceImpl.FacialEventServiceImpl;
import com.FaceLit.backend.notification.repository.NotificationRepository;
import com.FaceLit.backend.notification.service.NotificationService;

class ConfirmedAbsenceDeliveryTest {
    final FacialEventRepository events = mock(FacialEventRepository.class);
    final NotificationRepository notifications = mock(NotificationRepository.class);
    final NotificationService delivery = mock(NotificationService.class);
    final FacialEventServiceImpl attendance = mock(FacialEventServiceImpl.class);
    final TransactionTemplate tx = mock(TransactionTemplate.class);
    final ConfirmedAbsenceDelivery service = new ConfirmedAbsenceDelivery(events, notifications, delivery, attendance, tx);
    final FacialEvent event = new FacialEvent();
    ConfirmedAbsenceDeliveryTest() {
        doAnswer(call -> { Consumer<TransactionStatus> action = call.getArgument(0); action.accept(mock(TransactionStatus.class)); return null; }).when(tx).executeWithoutResult(any());
        event.setIdFacialEvent(UUID.randomUUID()); event.setEventType(FacialEventType.ENTRY); event.setAttendanceStatus(AttendanceStatus.ABSENT);
        RecordEnvironment session = new RecordEnvironment(); session.setIdRecordEnvironment(UUID.randomUUID()); session.setShutdownTime(OffsetDateTime.now().minusMinutes(1)); event.setRecordEnvironment(session);
        User user = new User(); user.setIdUser(UUID.randomUUID()); Apprentice apprentice = new Apprentice(); apprentice.setUser(user); event.setApprentice(apprentice);
        when(events.lockForNotification(event.getIdFacialEvent())).thenReturn(Optional.of(event));
    }
    @Test void confirmsAfterShutdownAndDoesNotRepeatAlreadyDelivered() {
        service.deliver(event.getIdFacialEvent());
        verify(delivery).createForRecipient(argThat(n -> n.type().equals("attendance_absent") && n.recipientUserId().equals(event.getApprentice().getUser().getIdUser())));
        when(notifications.existsByIdFacialEventAndType(event.getIdFacialEvent(), "attendance_absent")).thenReturn(true);
        service.deliver(event.getIdFacialEvent()); verifyNoMoreInteractions(delivery);
    }
    @Test void waitsUntilSessionEnds() { event.getRecordEnvironment().setShutdownTime(OffsetDateTime.now().plusMinutes(5)); service.deliver(event.getIdFacialEvent()); verifyNoInteractions(delivery); }
    @Test void skipsExcusedAbsentAndActualLateEntry() {
        event.setExcuse(true); service.deliver(event.getIdFacialEvent()); event.setExcuse(false); event.setAttendanceStatus(AttendanceStatus.LATE); service.deliver(event.getIdFacialEvent()); verifyNoInteractions(delivery);
    }
}
