package com.FaceLit.backend.auth;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.FaceLit.backend.auth.model.security.*;
import com.FaceLit.backend.auth.model.roleandpermission.*;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.repository.security.*;
import com.FaceLit.backend.auth.repository.roleandpermission.*;
import com.FaceLit.backend.auth.dto.request.security.ResetPasswordDTO;
import com.FaceLit.backend.auth.dto.request.roleandpermission.AssignRoleRequestDTO;
import com.FaceLit.backend.auth.service.serviceImpl.security.PasswordRecoveryServiceImpl;
import com.FaceLit.backend.auth.service.serviceImpl.roleandpermission.AdminRoleServiceImpl;
import com.FaceLit.backend.shared.service.*;
import com.FaceLit.backend.shared.util.VerificationCodeGenerator;
import com.FaceLit.backend.notification.service.NotificationService;

class AccountLifecycleNotificationTest {
    @Test void successfulRecoveryNotifiesOnceAndReusedCodeDoesNotNotifyAgain() {
        var credentials = mock(CredentialRepository.class); var recoveries = mock(PasswordRecoveryRepository.class);
        var encoder = mock(PasswordEncoder.class); var notices = mock(NotificationService.class);
        var challenges = mock(com.FaceLit.backend.auth.service.serviceImpl.security.RecoveryChallengeService.class);
        var sessions = mock(com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService.class);
        var service = new PasswordRecoveryServiceImpl(credentials, recoveries, encoder, mock(EmailService.class), mock(VerificationCodeGenerator.class), notices, challenges, sessions);
        User user = new User(); user.setIdUser(UUID.randomUUID()); user.setFirstName("Carlos"); user.setLastName("Ramirez");
        Credential credential = new Credential(); credential.setUser(user); user.setCredential(credential); credential.setLockedUntil(OffsetDateTime.now().plusMinutes(1));
        PasswordRecovery recovery = new PasswordRecovery(); recovery.setUser(user); recovery.setUsed(false); recovery.setExpirationDate(OffsetDateTime.now().plusMinutes(5));
        when(challenges.authorizeReset(any(), any())).thenAnswer(call -> { if (recovery.isUsed()) throw new RuntimeException("used"); return recovery; }); when(encoder.encode(anyString())).thenReturn("hashed");
        ResetPasswordDTO dto = new ResetPasswordDTO(); dto.setToken("123456"); dto.setNewPassword("Secret123!"); dto.setConfirmPassword("Secret123!");
        service.resetPassword(dto);
        verify(notices).createForRecipient(argThat(n -> n.type().equals("password_changed") && n.recipientUserId().equals(user.getIdUser()) && !n.message().contains("Secret123!")
                && n.message().contains("Carlos Ramirez") && n.metadataJson().contains("password_recovered") && n.metadataJson().contains("occurredAt")));
        assertThat(credential.getLockedUntil()).isNull();
        assertThatThrownBy(() -> service.resetPassword(dto)).isInstanceOf(RuntimeException.class); verifyNoMoreInteractions(notices);
    }
    @Test void roleChangeContainsOldNewAndAuthenticatedActorAndSameRoleDoesNotRepeat() {
        var users = mock(UserRepository.class); var roles = mock(UserRoleRepository.class); var catalog = mock(RoleRepository.class);
        var notices = mock(NotificationService.class); var names = mock(UserDisplayNameService.class);
        var service = new AdminRoleServiceImpl(users, roles, catalog, mock(CredentialRepository.class), notices, names, mock(com.FaceLit.backend.auth.service.serviceImpl.security.SessionSecurityService.class));
        UUID actor = UUID.randomUUID(); User user = new User(); user.setIdUser(UUID.randomUUID());
        Role old = new Role(); old.setNameRole(RoleName.APPRENTICE); Role next = new Role(); next.setNameRole(RoleName.INSTRUCTOR);
        UserRole assigned = new UserRole(); assigned.setRole(old); assigned.setUser(user);
        when(users.findById(user.getIdUser())).thenReturn(Optional.of(user)); when(roles.findByUserId(user.getIdUser())).thenReturn(Optional.of(assigned));
        when(catalog.findByNameRole(RoleName.INSTRUCTOR)).thenReturn(Optional.of(next)); when(names.fullName(actor)).thenReturn("Maria Rodriguez");
        AssignRoleRequestDTO dto = new AssignRoleRequestDTO(); dto.setRole(RoleName.INSTRUCTOR);
        service.assignRole(user.getIdUser(), dto, actor);
        verify(notices).createForRecipient(argThat(n -> n.type().equals("user_role_changed") && n.metadataJson().contains("APPRENTICE") && n.metadataJson().contains("INSTRUCTOR") && n.message().contains("Maria Rodriguez")));
        service.assignRole(user.getIdUser(), dto, actor); verifyNoMoreInteractions(notices);
    }
}
