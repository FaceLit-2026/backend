package com.FaceLit.backend.environment;

import static org.assertj.core.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import jakarta.persistence.EntityManager;
import com.FaceLit.backend.academic.model.academic.*;
import com.FaceLit.backend.academic.model.enums.*;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.model.enums.AccountStatus;
import com.FaceLit.backend.environment.model.Environment;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.environment.model.enums.EnvironmentState;
import com.FaceLit.backend.environment.repository.RecordEnvironmentRepository;
import com.FaceLit.backend.facial.model.*;
import com.FaceLit.backend.facial.model.enums.*;
import com.FaceLit.backend.facial.repository.FacialEventRepository;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class ExitReminderRepositoryTest {
    @Autowired EntityManager em;
    @Autowired FacialEventRepository attendance;
    @Autowired RecordEnvironmentRepository sessions;
    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-15T17:55:00-05:00");

    @Test void actualQueryIncludesPunctualAndLateAndExcludesAbsentDeletedFutureAndExitOnly() {
        var session = session();
        User punctual = event(session, AttendanceStatus.PUNCTUAL, FacialEventType.ENTRY, now.minusHours(1));
        User late = event(session, AttendanceStatus.LATE, FacialEventType.ENTRY, now.minusMinutes(10));
        event(session, AttendanceStatus.ABSENT, FacialEventType.ENTRY, now.minusHours(1));
        event(session, AttendanceStatus.PUNCTUAL, FacialEventType.EXIT, now.minusMinutes(2));
        event(session, AttendanceStatus.LATE, FacialEventType.ENTRY, now.plusMinutes(1));
        User deleted = event(session, AttendanceStatus.PUNCTUAL, FacialEventType.ENTRY, now.minusMinutes(2));
        deleted.setDeletedAt(now);
        em.flush(); em.clear();
        assertThat(attendance.findExitReminderAttendees(session.getIdRecordEnvironment(), now))
                .containsExactlyInAnyOrder(punctual.getIdUser(), late.getIdUser());
    }

    @Test void actualQueryOnlySelectsActiveSessionsInFiveMinuteWindow() {
        var due = session();
        var later = session(); later.setExitTime(now.plusMinutes(5).plusSeconds(1));
        var finished = session(); finished.setExitTime(now);
        var inactive = session(); inactive.setActive(false);
        var deleted = session(); deleted.setDeletedAt(now);
        em.flush(); em.clear();
        assertThat(sessions.findExitReminderCandidates(now, now.plusMinutes(5))).containsExactly(due.getIdRecordEnvironment());
    }

    @Test void confirmedAbsenceQueryExcludesOpenSessionsExcusesAndAlreadyNotifiedEvents() {
        var ended = session(); ended.setExitTime(now.minusMinutes(5)); ended.setShutdownTime(now.minusSeconds(1));
        User absent = event(ended, AttendanceStatus.ABSENT, FacialEventType.ENTRY, now.minusHours(1));
        User excused = event(ended, AttendanceStatus.ABSENT, FacialEventType.ENTRY, now.minusHours(1));
        User notified = event(ended, AttendanceStatus.ABSENT, FacialEventType.ENTRY, now.minusHours(1));
        event(ended, AttendanceStatus.LATE, FacialEventType.ENTRY, now.minusMinutes(30));
        event(session(), AttendanceStatus.ABSENT, FacialEventType.ENTRY, now.minusHours(1));
        FacialEvent excusedEvent = em.createQuery("select e from FacialEvent e where e.apprentice.user.idUser = :user", FacialEvent.class).setParameter("user", excused.getIdUser()).getSingleResult();
        excusedEvent.setExcuse(true);
        FacialEvent notifiedEvent = em.createQuery("select e from FacialEvent e where e.apprentice.user.idUser = :user", FacialEvent.class).setParameter("user", notified.getIdUser()).getSingleResult();
        var notice = new com.FaceLit.backend.notification.model.Notification(); notice.setIdUserApp(notified.getIdUser());
        notice.setIdFacialEvent(notifiedEvent.getIdFacialEvent()); notice.setType("attendance_absent"); notice.setTitle("Ausencia");
        notice.setMessage("Ausencia confirmada"); notice.setCategory("ATTENDANCE"); notice.setChannel("APP_EMAIL"); em.persist(notice);
        UUID expected = em.createQuery("select e.idFacialEvent from FacialEvent e where e.apprentice.user.idUser = :user", UUID.class).setParameter("user", absent.getIdUser()).getSingleResult();
        em.flush(); em.clear();
        assertThat(attendance.findUnnotifiedConfirmedAbsences(now, org.springframework.data.domain.PageRequest.of(0, 100))).containsExactly(expected);
    }

    @Autowired com.FaceLit.backend.facial.repository.FaceResetRequestRepository resets;
    @Test void expirationQueryOnlySelectsUnusedUnnotifiedExpiredPermissions() {
        var expired = permission(now.minusMinutes(1));
        permission(now.plusMinutes(1));
        var used = permission(now.minusMinutes(1)); used.setConsumedAt(now.minusMinutes(2));
        var notified = permission(now.minusMinutes(1));
        var notice = new com.FaceLit.backend.notification.model.Notification(); notice.setIdUserApp(notified.getUserId());
        notice.setType("facial_authorization_expired"); notice.setTitle("Vencido"); notice.setMessage("Permiso vencido"); notice.setCategory("FACIAL_RECOGNITION"); notice.setChannel("APP");
        notice.setReferenceEntity("face_reset_request"); notice.setReferenceId(notified.getId()); em.persist(notice);
        em.flush(); em.clear(); assertThat(resets.findUnnotifiedExpired(now)).containsExactly(expired.getId());
    }
    private com.FaceLit.backend.facial.model.FaceResetRequest permission(OffsetDateTime expires) {
        var request = new com.FaceLit.backend.facial.model.FaceResetRequest(); request.setUserId(user().getIdUser());
        request.setStatus("ACCEPTED"); request.setAuthorizationExpiresAt(expires); request.setAuthorizationToken(UUID.randomUUID());
        em.persist(request); return request;
    }

    private RecordEnvironment session() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Program program = new Program(); program.setProgramName("Program " + unique); program.setProgramCode(unique); program.setState(AcademicState.ACTIVE); em.persist(program);
        Chip chip = new Chip(); chip.setChipCode(unique); chip.setProgram(program); chip.setState(AcademicState.ACTIVE); em.persist(chip);
        Environment environment = new Environment(); environment.setEnvironmentName("Room " + unique); environment.setState(EnvironmentState.ACTIVE); em.persist(environment);
        Device device = new Device(); device.setDeviceCode(unique); device.setStatus("ACTIVE"); device.setEnvironment(environment); em.persist(device);
        Instructor instructor = new Instructor(); instructor.setUser(user()); instructor.setInstructorType(InstructorType.ESPECIFICO); em.persist(instructor);
        RecordEnvironment record = new RecordEnvironment(); record.setChip(chip); record.setEnvironment(environment); record.setDevice(device);
        record.setInstructorScheduled(instructor); record.setEntryTime(now.minusHours(1)); record.setExitTime(now.plusMinutes(5));
        record.setShutdownTime(now.plusMinutes(15)); record.setRegistrationMinutes(10); record.setExitReminderSent(false); record.setActive(true); em.persist(record);
        return record;
    }

    private User event(RecordEnvironment record, AttendanceStatus status, FacialEventType type, OffsetDateTime time) {
        User user = user(); Apprentice apprentice = new Apprentice(); apprentice.setUser(user); em.persist(apprentice);
        FacialEvent event = new FacialEvent(); event.setApprentice(apprentice); event.setRecordEnvironment(record); event.setDevice(record.getDevice());
        event.setEventType(type); event.setAttendanceStatus(status); event.setEventDatetime(time); event.setRecognitionResult("TEST"); event.setOrigin(FacialEventOrigin.PC); em.persist(event);
        return user;
    }

    private User user() {
        User user = new User(); user.setDocumentNumber(UUID.randomUUID().toString().substring(0, 12)); user.setFirstName("Test"); user.setLastName("Reminder"); user.setAccountStatus(AccountStatus.ACTIVE); em.persist(user); return user;
    }
}
