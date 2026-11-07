package com.FaceLit.backend.environment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.FaceLit.backend.academic.model.academic.*;
import com.FaceLit.backend.academic.model.enums.AcademicState;
import com.FaceLit.backend.academic.repository.*;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.service.roleandpermission.LoginService;
import com.FaceLit.backend.environment.dto.request.RecordEnvironmentRequestDTO;
import com.FaceLit.backend.environment.model.*;
import com.FaceLit.backend.environment.model.enums.EnvironmentState;
import com.FaceLit.backend.environment.repository.*;
import com.FaceLit.backend.environment.service.serviceImpl.EnvironmentServiceImpl;
import com.FaceLit.backend.facial.model.Device;
import com.FaceLit.backend.facial.repository.DeviceRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.service.*;

class SessionNotificationTest {
    private final EnvironmentRepository environments = mock(EnvironmentRepository.class);
    private final RecordEnvironmentRepository sessions = mock(RecordEnvironmentRepository.class);
    private final ChipEnvironmentRepository chipEnvironments = mock(ChipEnvironmentRepository.class);
    private final InstructorRepository instructors = mock(InstructorRepository.class);
    private final ChipRepository chips = mock(ChipRepository.class);
    private final UserChipRepository learners = mock(UserChipRepository.class);
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final EnvironmentServiceImpl service = new EnvironmentServiceImpl(environments, sessions, chipEnvironments, instructors,
            mock(InstructorProgramRepository.class), chips, learners, devices, mock(LoginService.class),
            new EnvironmentSessionNotifier(instructors, learners, notifications),
            mock(com.FaceLit.backend.facial.repository.FacialEventRepository.class));
    private final Instructor creator = instructor("Camila", "Perez");
    private final Instructor selected = instructor("Luis", "Gomez");
    private final RecordEnvironmentRequestDTO dto = new RecordEnvironmentRequestDTO();
    private RecordEnvironment saved;

    @BeforeEach void setup() {
        Environment environment = new Environment();
        environment.setIdEnvironment(UUID.randomUUID()); environment.setEnvironmentName("Aula \"2\"");
        environment.setState(EnvironmentState.ACTIVE);
        Chip chip = new Chip(); chip.setIdChip(UUID.randomUUID()); chip.setChipCode("3145555"); chip.setState(AcademicState.ACTIVE);
        Program program = new Program(); program.setIdProgram(UUID.randomUUID()); program.setState(AcademicState.ACTIVE);
        chip.setProgram(program);
        Device device = new Device(); device.setIdDevice(UUID.randomUUID()); device.setDeviceCode("camera-1");
        device.setEnvironment(environment); device.setStatus("ACTIVE");
        dto.setIdEnvironment(environment.getIdEnvironment()); dto.setIdChip(chip.getIdChip());
        dto.setIdInstructorInCharge(selected.getIdInstructor()); dto.setDeviceCode(device.getDeviceCode()); dto.setRegistrationMinutes(10);
        dto.setEntryTime(OffsetDateTime.now().plusMinutes(5)); dto.setExitTime(dto.getEntryTime().plusHours(2));
        dto.setShutdownTime(dto.getExitTime().plusMinutes(10));
        when(environments.findById(dto.getIdEnvironment())).thenReturn(Optional.of(environment));
        when(chips.findById(dto.getIdChip())).thenReturn(Optional.of(chip));
        when(instructors.findById(selected.getIdInstructor())).thenReturn(Optional.of(selected));
        when(instructors.findByUser_IdUser(creator.getUser().getIdUser())).thenReturn(Optional.of(creator));
        when(learners.countActiveRegisteredFacesByChip(chip.getIdChip())).thenReturn(1L);
        when(devices.findByDeviceCode(device.getDeviceCode())).thenReturn(Optional.of(device));
        when(devices.save(any())).thenAnswer(call -> call.getArgument(0));
        when(sessions.save(any())).thenAnswer(call -> {
            saved = call.getArgument(0);
            if (saved.getIdRecordEnvironment() == null) saved.setIdRecordEnvironment(UUID.randomUUID());
            return saved;
        });
    }

    @Test void differentCreatorAndResponsibleBothReceiveCompleteAppAndEmailNotice() throws Exception {
        service.createSession(dto, creator.getUser().getIdUser());
        var capture = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(notifications, times(2)).createForRecipient(capture.capture());
        assertThat(capture.getAllValues()).extracting(CreateNotificationRequestDTO::recipientUserId)
                .containsExactlyInAnyOrder(creator.getUser().getIdUser(), selected.getUser().getIdUser());
        for (var notice : capture.getAllValues()) {
            assertThat(notice.type()).isEqualTo("facial_session_substitution");
            assertThat(new NotificationCatalog().require(notice.type()).channel()).isEqualTo("APP_EMAIL");
            assertThat(notice.message()).contains("Camila Perez", "Luis Gomez", "3145555", "Aula \"2\"", "Inicio:", "Salida:");
            var meta = new com.fasterxml.jackson.databind.ObjectMapper().readTree(notice.metadataJson());
            assertThat(meta.get("configuredByName").asText()).isEqualTo("Camila Perez");
            assertThat(meta.get("instructorName").asText()).isEqualTo("Luis Gomez");
            assertThat(meta.get("environment").asText()).isEqualTo("Aula \"2\"");
        }
    }

    @Test void sameInstructorReceivesOnlyOneNotice() {
        when(instructors.findByUser_IdUser(selected.getUser().getIdUser())).thenReturn(Optional.of(selected));
        service.createSession(dto, selected.getUser().getIdUser());
        var capture = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(notifications).createForRecipient(capture.capture());
        assertThat(capture.getValue().recipientUserId()).isEqualTo(selected.getUser().getIdUser());
        assertThat(capture.getValue().type()).isEqualTo("session_assigned");
        assertThat(capture.getValue().message()).contains("Luis Gomez", "3145555");
    }

    @Test void changingSelectedInstructorNotifiesPreviousAndNewResponsibleEvenWhenCreatorIsUnchanged() {
        service.createSession(dto, creator.getUser().getIdUser());
        clearInvocations(notifications);
        Instructor replacement = instructor("Maria", "Rodriguez");
        when(instructors.findById(replacement.getIdInstructor())).thenReturn(Optional.of(replacement));
        when(sessions.findById(saved.getIdRecordEnvironment())).thenReturn(Optional.of(saved));
        dto.setIdInstructorInCharge(replacement.getIdInstructor());
        service.updateSession(saved.getIdRecordEnvironment(), dto, creator.getUser().getIdUser());
        var capture = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(notifications, times(3)).createForRecipient(capture.capture());
        assertThat(capture.getAllValues()).extracting(CreateNotificationRequestDTO::recipientUserId)
                .containsExactlyInAnyOrder(creator.getUser().getIdUser(), selected.getUser().getIdUser(), replacement.getUser().getIdUser());
        assertThat(capture.getAllValues()).allSatisfy(notice -> {
            assertThat(notice.type()).isEqualTo("session_updated");
            assertThat(notice.message()).contains("Camila Perez", "Maria Rodriguez", "responsable");
        });
    }

    private static Instructor instructor(String first, String last) {
        User user = new User(); user.setIdUser(UUID.randomUUID()); user.setFirstName(first); user.setLastName(last);
        user.setAccountStatus(com.FaceLit.backend.auth.model.enums.AccountStatus.ACTIVE);
        Instructor instructor = new Instructor(); instructor.setIdInstructor(UUID.randomUUID()); instructor.setUser(user);
        instructor.setInstructorType(com.FaceLit.backend.academic.model.enums.InstructorType.TRANSVERSAL);
        return instructor;
    }
}
