package com.FaceLit.backend.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.model.roleandpermission.*;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.shared.service.UserDisplayNameService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ProfileChangeNotificationTest {
    NotificationService notifications = mock(NotificationService.class);
    UserRoleRepository roles = mock(UserRoleRepository.class);
    UserDisplayNameService names = mock(UserDisplayNameService.class);
    ObjectMapper mapper = new ObjectMapper();
    ProfileChangeNotificationService service = new ProfileChangeNotificationService(notifications, roles, names, mapper);
    User owner = new User();
    @BeforeEach void setup() { owner.setIdUser(UUID.randomUUID()); owner.setFirstName("Maria"); owner.setLastName("Prueba"); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void changedFieldsIncludeBeforeAfterActorAndTimeForOwnerAndResponsibleCoordinatorOnly() throws Exception {
        UUID actor = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        Role role = new Role(); role.setNameRole(RoleName.COORDINATOR); UserRole assigned = new UserRole(); assigned.setRole(role);
        when(roles.findByUserId(actor)).thenReturn(Optional.of(assigned)); when(names.fullName(actor)).thenReturn("Ana Coordinadora");
        service.notifyChanges(owner, "apprentice_profile_updated", "learner", Map.of(
                "nombre", new String[]{"Maria", "Maria Jose"}, "apellido", new String[]{"Prueba", "Prueba"},
                "correo", new String[]{"old@example.com", "new@example.com"}, "password", new String[]{"SECRET", "SECRET2"}));
        var capture = ArgumentCaptor.forClass(CreateNotificationRequestDTO.class);
        verify(notifications, times(2)).createForRecipient(capture.capture()); verifyNoMoreInteractions(notifications);
        assertThat(capture.getAllValues()).extracting(CreateNotificationRequestDTO::recipientUserId).containsExactly(owner.getIdUser(), actor);
        var metadata = mapper.readTree(capture.getValue().metadataJson());
        assertThat(metadata.path("profileChanges").size()).isEqualTo(2);
        assertThat(metadata.path("changedByName").asText()).isEqualTo("Ana Coordinadora");
        assertThat(metadata.path("occurredAt").asText()).isNotBlank();
        assertThat(capture.getValue().message()).contains("Maria Jose", "old@example.com", "new@example.com", "hora de Colombia");
        assertThat(capture.getValue().metadataJson()).doesNotContain("SECRET", "password");
    }

    @Test void unchangedOrSensitiveFieldsDoNotCreateNotices() {
        service.notifyChanges(owner, "user_profile_updated", "user", Map.of("nombre", new String[]{"Maria", "Maria"},
                "token", new String[]{"secret", "other"}));
        verifyNoInteractions(notifications);
    }

    @Test void authenticatedNonCoordinatorDoesNotReceiveAnotherUsersCopy() {
        UUID actor = UUID.randomUUID(); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        Role role = new Role(); role.setNameRole(RoleName.INSTRUCTOR); UserRole assigned = new UserRole(); assigned.setRole(role);
        when(roles.findByUserId(actor)).thenReturn(Optional.of(assigned)); when(names.fullName(actor)).thenReturn("Instructor");
        service.notifyChanges(owner, "user_profile_updated", "user", Map.of("nombre", new String[]{"Maria", "Maria Jose"}));
        verify(notifications).createForRecipient(argThat(n -> n.recipientUserId().equals(owner.getIdUser()))); verifyNoMoreInteractions(notifications);
    }

    @Test void internalRoleCodesArePresentedInSpanish() {
        assertThat(ProfileChangeNotificationService.roleLabel("APPRENTICE")).isEqualTo("Aprendiz");
        assertThat(ProfileChangeNotificationService.roleLabel("INSTRUCTOR")).isEqualTo("Instructor");
        assertThat(ProfileChangeNotificationService.roleLabel("COORDINATOR")).isEqualTo("Coordinador");
    }

    @Test void informationalNotificationFailureDoesNotRejectProfileUpdate() {
        when(notifications.createForRecipient(any())).thenThrow(new IllegalStateException("simulated failure"));
        assertThatCode(() -> service.notifyChanges(owner, "user_profile_updated", "user",
                Map.of("nombre", new String[]{"Maria", "Maria Jose"}))).doesNotThrowAnyException();
    }
}
