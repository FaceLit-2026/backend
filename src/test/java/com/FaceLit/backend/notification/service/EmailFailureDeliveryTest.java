package com.FaceLit.backend.notification.service;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.model.roleandpermission.UserRole;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.notification.model.*;
import com.FaceLit.backend.notification.repository.*;
import com.FaceLit.backend.shared.service.UserDisplayNameService;

class EmailFailureDeliveryTest {
    final EmailLogRepository logs = mock(EmailLogRepository.class);
    final NotificationRepository notices = mock(NotificationRepository.class);
    final UserRoleRepository roles = mock(UserRoleRepository.class);
    final NotificationService delivery = mock(NotificationService.class);
    final UserDisplayNameService names = mock(UserDisplayNameService.class);
    final TransactionTemplate tx = mock(TransactionTemplate.class);
    final EmailFailureDelivery service = new EmailFailureDelivery(logs, notices, roles, delivery, names, tx);
    final Notification original = new Notification();
    final EmailLog email = new EmailLog();
    final UUID coordinator = UUID.randomUUID();
    EmailFailureDeliveryTest() {
        original.setIdNotification(UUID.randomUUID()); original.setIdUserApp(UUID.randomUUID()); original.setType("facial_request_resolved"); original.setTitle("Registro autorizado");
        email.setIdNotification(original.getIdNotification()); email.setSendStatus(EmailLog.SendStatus.FAILED); email.setAttempts(3);
        when(logs.lockByNotification(original.getIdNotification())).thenReturn(Optional.of(email));
        when(notices.findById(original.getIdNotification())).thenReturn(Optional.of(original));
        when(names.fullName(original.getIdUserApp())).thenReturn("Mariana Lopez");
        when(roles.findByRoleName(RoleName.COORDINATOR)).thenReturn(List.of(role(coordinator), role(UUID.randomUUID())));
        doAnswer(call -> { Consumer<TransactionStatus> action = call.getArgument(0); action.accept(mock(TransactionStatus.class)); return null; }).when(tx).executeWithoutResult(any());
    }
    @Test void notifiesResolvingCoordinatorOnceAndNeverEmailsTheFailure() {
        original.setMetadataJson("{\"decidedBy\":\"" + coordinator + "\"}");
        service.deliver(original.getIdNotification());
        verify(delivery).createForRecipient(argThat(n -> n.recipientUserId().equals(coordinator) && n.type().equals("email_delivery_failed") && n.message().contains("Mariana Lopez")));
        assertThat(new NotificationCatalog().require("email_delivery_failed").channel()).isEqualTo("APP");
        Notification existing = new Notification(); existing.setIdUserApp(coordinator); existing.setType("email_delivery_failed");
        when(notices.findByReferenceEntityAndReferenceId("failed_notification", original.getIdNotification())).thenReturn(List.of(existing));
        service.deliver(original.getIdNotification()); verifyNoMoreInteractions(delivery);
    }
    @Test void withoutAssignedCoordinatorNotifiesCoordinationTeam() { service.deliver(original.getIdNotification()); verify(delivery, times(2)).createForRecipient(any()); }
    @Test void pendingRetryAndCancelledDoNotEscalate() {
        email.setAttempts(2); service.deliver(original.getIdNotification());
        email.setAttempts(3); email.setSendStatus(EmailLog.SendStatus.CANCELLED); service.deliver(original.getIdNotification()); verifyNoInteractions(delivery);
    }
    private UserRole role(UUID id) { User u = new User(); u.setIdUser(id); UserRole r = new UserRole(); r.setUser(u); return r; }
}
