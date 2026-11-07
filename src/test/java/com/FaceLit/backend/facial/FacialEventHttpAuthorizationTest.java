package com.FaceLit.backend.facial;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.FaceLit.backend.academic.model.academic.*;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.*;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.service.roleandpermission.JwtService;
import com.FaceLit.backend.auth.service.serviceImpl.security.RecoveryRateLimiter;
import com.FaceLit.backend.config.*;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.environment.repository.RecordEnvironmentRepository;
import com.FaceLit.backend.facial.controller.FacialEventController;
import com.FaceLit.backend.facial.model.Device;
import com.FaceLit.backend.facial.repository.*;
import com.FaceLit.backend.facial.service.serviceImpl.FacialEventServiceImpl;
import com.FaceLit.backend.facial.service.AttendanceMatrixService;
import com.FaceLit.backend.facial.service.AttendanceNotificationService;
import com.FaceLit.backend.facial.service.FacialAccessPolicy;
import com.FaceLit.backend.notification.service.NotificationService;
import com.FaceLit.backend.shared.exception.GlobalExceptionHandler;

@WebMvcTest(FacialEventController.class)
@Import({SecurityConfig.class, JwtFilter.class, FacialEventServiceImpl.class, GlobalExceptionHandler.class,
        AttendanceMatrixService.class, AttendanceNotificationService.class, FacialAccessPolicy.class,
        AttendanceReadRepository.class, AttendanceWriteRepository.class})
class FacialEventHttpAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwt;
    @MockitoBean RecoveryRateLimiter limiter;
    @MockitoBean FacialEventRepository events;
    @MockitoBean RecordEnvironmentRepository sessions;
    @MockitoBean DeviceRepository devices;
    @MockitoBean ApprenticeRepository apprentices;
    @MockitoBean UserChipRepository assignments;
    @MockitoBean InstructorRepository instructors;
    @MockitoBean FacialEmbeddingVerificationClient verification;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean NotificationService notifications;
    @MockitoBean com.FaceLit.backend.realtime.RealtimeWebSocketHandler realtime;
    final UUID sessionId = UUID.randomUUID(), actor = UUID.randomUUID(), learner = UUID.randomUUID(), apprenticeId = UUID.randomUUID();
    final RecordEnvironment session = new RecordEnvironment();
    final Apprentice apprentice = new Apprentice();

    @BeforeEach void prepare() {
        User teacher = new User(); teacher.setIdUser(actor);
        Instructor instructor = new Instructor(); instructor.setIdInstructor(UUID.randomUUID()); instructor.setUser(teacher);
        Chip chip = new Chip(); chip.setIdChip(UUID.randomUUID());
        session.setIdRecordEnvironment(sessionId); session.setInstructorScheduled(instructor); session.setChip(chip);
        session.setEntryTime(OffsetDateTime.now().minusMinutes(5)); session.setExitTime(OffsetDateTime.now().plusHours(1));
        session.setShutdownTime(OffsetDateTime.now().plusHours(2)); session.setActive(true);
        Device device = new Device(); device.setIdDevice(UUID.randomUUID()); session.setDevice(device);
        User owner = new User(); owner.setIdUser(learner); apprentice.setIdApprentice(apprenticeId); apprentice.setUser(owner);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(apprentices.findById(apprenticeId)).thenReturn(Optional.of(apprentice));
        when(assignments.existsByApprentice_IdApprenticeAndChip_IdChipAndState(apprenticeId, chip.getIdChip(), AcademicState.ACTIVE)).thenReturn(true);
    }

    RequestPostProcessor as(UUID user, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    String body() {
        return "{\"idRecordEnvironment\":\""+sessionId+"\",\"idDevice\":\""+session.getDevice().getIdDevice()
                +"\",\"idApprentice\":\""+apprenticeId+"\",\"recognitionResult\":\"MATCH\",\"origin\":\"PC\",\"excuse\":true}";
    }

    @Test void anonymousRequestsReceive401() throws Exception {
        mvc.perform(post("/api/facial/events").contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/facial/events/attendance-status").param("idRecordEnvironment",sessionId.toString()).param("idApprentice",apprenticeId.toString())).andExpect(status().isUnauthorized());
    }

    @Test void apprenticeCannotWriteAttendance() throws Exception {
        mvc.perform(post("/api/facial/events").with(as(learner,"APPRENTICE")).contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(post("/api/facial/events/from-image").with(as(learner,"APPRENTICE")).contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(post("/api/facial/events/initialize-absences").with(as(learner,"APPRENTICE")).param("idRecordEnvironment",sessionId.toString())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/facial/events/excuse").with(as(learner,"APPRENTICE")).contentType("application/json").content(body())).andExpect(status().isForbidden());
        verifyNoInteractions(events,verification,jdbc);
    }

    @Test void unrelatedInstructorCannotWriteOrReadSession() throws Exception {
        UUID stranger=UUID.randomUUID();
        mvc.perform(post("/api/facial/events").with(as(stranger,"INSTRUCTOR")).contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(post("/api/facial/events/from-image").with(as(stranger,"INSTRUCTOR")).contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(post("/api/facial/events/initialize-absences").with(as(stranger,"INSTRUCTOR")).param("idRecordEnvironment",sessionId.toString())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/facial/events/excuse").with(as(stranger,"INSTRUCTOR")).contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(get("/api/facial/events/attendance-status").with(as(stranger,"INSTRUCTOR")).param("idRecordEnvironment",sessionId.toString()).param("idApprentice",apprenticeId.toString())).andExpect(status().isForbidden());
        verifyNoInteractions(events,verification,jdbc);
    }

    @Test void responsibleInstructorAndCoordinatorCanInitialize() throws Exception {
        mvc.perform(post("/api/facial/events/initialize-absences").with(as(actor,"INSTRUCTOR")).param("idRecordEnvironment",sessionId.toString())).andExpect(status().isNoContent());
        mvc.perform(post("/api/facial/events/initialize-absences").with(as(UUID.randomUUID(),"COORDINATOR")).param("idRecordEnvironment",sessionId.toString())).andExpect(status().isNoContent());
        verify(jdbc,times(2)).update(anyString(),any(Object[].class));
    }

    @Test void apprenticeCanReadOnlyOwnAttendance() throws Exception {
        mvc.perform(get("/api/facial/events/attendance-status").with(as(learner,"APPRENTICE")).param("idRecordEnvironment",sessionId.toString()).param("idApprentice",apprenticeId.toString())).andExpect(status().isOk());
        mvc.perform(get("/api/facial/events/attendance-status").with(as(UUID.randomUUID(),"APPRENTICE")).param("idRecordEnvironment",sessionId.toString()).param("idApprentice",apprenticeId.toString())).andExpect(status().isForbidden());
    }

    @Test void apprenticeMatrixIsFilteredInDatabase() throws Exception {
        UserChip link=new UserChip(); link.setChip(session.getChip());
        when(assignments.findByApprentice_User_IdUserAndState(learner,AcademicState.ACTIVE)).thenReturn(Optional.of(link));
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.PreparedStatementSetter.class),any(org.springframework.jdbc.core.RowMapper.class))).thenAnswer(call -> {
            java.sql.PreparedStatement statement=mock(java.sql.PreparedStatement.class);
            ((org.springframework.jdbc.core.PreparedStatementSetter)call.getArgument(1)).setValues(statement);
            verify(statement).setObject(7,learner); verify(statement).setObject(8,learner);
            return List.of();
        });
        mvc.perform(get("/api/facial/events/attendance-matrix").with(as(learner,"APPRENTICE")).param("idChip",session.getChip().getIdChip().toString()).param("dateFrom","2026-10-01").param("dateTo","2026-10-26")).andExpect(status().isOk());
    }
}
