package com.FaceLit.backend.facial;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.model.roleandpermission.*;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.shared.service.UserDisplayNameService;
import com.FaceLit.backend.facial.repository.FacialEventRepository;
import com.FaceLit.backend.facial.model.FacialEvent;
import com.FaceLit.backend.facial.model.enums.*;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.academic.model.academic.*;
import com.FaceLit.backend.facial.service.serviceImpl.FacialEventServiceImpl;
import com.FaceLit.backend.notification.service.NotificationService;

class AttendanceCorrectionServiceTest {
    @Test void exitCorrectionRetainsPreviousTimeAndCannotPrecedeEntry() {
        var entry = new FacialEvent(); entry.setAttendanceStatus(AttendanceStatus.LATE); entry.setEventDatetime(session.getEntryTime().plusMinutes(30));
        when(events.findFirstByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNullOrderByEventDatetimeAsc(
                session.getIdRecordEnvironment(), event.getApprentice().getIdApprentice(), FacialEventType.ENTRY)).thenReturn(Optional.of(entry));
        event.setEventType(FacialEventType.EXIT); event.setAttendanceStatus(AttendanceStatus.LATE); event.setEventDatetime(OffsetDateTime.now().minusMinutes(20));
        var before = event.getEventDatetime();
        service.correct(event.getIdFacialEvent(), new AttendanceCorrectionService.Correction(AttendanceStatus.LATE, OffsetDateTime.now().minusMinutes(10), false, "Hora de salida revisada"), actor);
        verify(notices, times(2)).createForRecipient(argThat(n -> n.message().contains("salida") && n.metadataJson().contains(before.toString()) && n.metadataJson().contains("Ana Gomez")));
        assertThatThrownBy(() -> service.correct(event.getIdFacialEvent(), new AttendanceCorrectionService.Correction(AttendanceStatus.LATE, session.getEntryTime().plusMinutes(20), false, "Revisada"), actor)).hasMessageContaining("entrada");
    }
    @Test void correctionCannotMarkAbsentWhenAnExitExists() {
        var exit = new FacialEvent(); exit.setEventDatetime(OffsetDateTime.now().minusMinutes(1));
        when(events.findFirstByRecordEnvironment_IdRecordEnvironmentAndApprentice_IdApprenticeAndEventTypeAndDeletedAtIsNullOrderByEventDatetimeAsc(
                session.getIdRecordEnvironment(), event.getApprentice().getIdApprentice(), FacialEventType.EXIT)).thenReturn(Optional.of(exit));
        assertThatThrownBy(() -> service.correct(event.getIdFacialEvent(), new AttendanceCorrectionService.Correction(AttendanceStatus.ABSENT, null, false, "Revisada"), actor)).hasMessageContaining("salida");
        verifyNoInteractions(notices); verify(events, never()).saveAndFlush(any());
    }
    final FacialEventRepository events = mock(FacialEventRepository.class);
    final UserRoleRepository roles = mock(UserRoleRepository.class);
    final UserDisplayNameService names = mock(UserDisplayNameService.class);
    final FacialEventServiceImpl attendance = mock(FacialEventServiceImpl.class);
    final NotificationService notices = mock(NotificationService.class);
    final AttendanceCorrectionService service = new AttendanceCorrectionService(events, roles, names, attendance, notices);
    final UUID actor = UUID.randomUUID(); final FacialEvent event = new FacialEvent(); final RecordEnvironment session = new RecordEnvironment();
    AttendanceCorrectionServiceTest() {
        User learner = new User(); learner.setIdUser(UUID.randomUUID()); Apprentice apprentice = new Apprentice(); apprentice.setIdApprentice(UUID.randomUUID()); apprentice.setUser(learner);
        Instructor instructor = new Instructor(); User teacher = new User(); teacher.setIdUser(actor); instructor.setUser(teacher);
        session.setInstructorScheduled(instructor); session.setIdRecordEnvironment(UUID.randomUUID()); session.setEntryTime(OffsetDateTime.now().minusHours(2));
        session.setExitTime(OffsetDateTime.now().plusMinutes(30)); session.setShutdownTime(OffsetDateTime.now().plusHours(1)); session.setRegistrationMinutes(10);
        event.setIdFacialEvent(UUID.randomUUID()); event.setRecordEnvironment(session); event.setDevice(new com.FaceLit.backend.facial.model.Device()); event.setApprentice(apprentice);
        event.setEventType(FacialEventType.ENTRY); event.setAttendanceStatus(AttendanceStatus.ABSENT); event.setEventDatetime(session.getEntryTime()); event.setExcuse(false);
        when(events.lockForNotification(event.getIdFacialEvent())).thenReturn(Optional.of(event));
        Role role = new Role(); role.setNameRole(RoleName.INSTRUCTOR); UserRole assigned = new UserRole(); assigned.setRole(role); when(roles.findByUserId(actor)).thenReturn(Optional.of(assigned));
        when(names.fullName(actor)).thenReturn("Ana Gomez"); when(attendance.metadataFor(event, "corrected")).thenReturn("{\"learnerName\":\"Carlos Ramirez\",\"sessionDate\":\"2026-10-16\"}");
        when(attendance.attendanceContext(event)).thenReturn("Ficha 3145555. Aprendiz: Carlos Ramirez");
    }
    @Test void correctionNotifiesLearnerAndSelectedInstructorWithBeforeAfterAndActor() {
        var correction = new AttendanceCorrectionService.Correction(AttendanceStatus.LATE, session.getEntryTime().plusMinutes(30), false, "Entrada revisada");
        service.correct(event.getIdFacialEvent(), correction, actor);
        verify(notices, times(2)).createForRecipient(argThat(n -> n.type().equals("attendance_corrected") && n.metadataJson().contains("ABSENT") && n.metadataJson().contains("LATE") && n.message().contains("Ana Gomez") && n.message().contains("Entrada revisada")));
        service.correct(event.getIdFacialEvent(), correction, actor); verifyNoMoreInteractions(notices);
    }
    @Test void unauthorizedActorAndInconsistentTimeDoNotModifyOrNotify() {
        var correction = new AttendanceCorrectionService.Correction(AttendanceStatus.PUNCTUAL, session.getEntryTime().plusMinutes(30), false, "Revisada");
        assertThatThrownBy(() -> service.correct(event.getIdFacialEvent(), correction, UUID.randomUUID())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.correct(event.getIdFacialEvent(), correction, actor)).hasMessageContaining("estado");
        verify(events, never()).saveAndFlush(any()); verifyNoInteractions(notices);
    }
}
