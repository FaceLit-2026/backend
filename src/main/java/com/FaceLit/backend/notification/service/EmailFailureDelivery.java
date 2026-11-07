package com.FaceLit.backend.notification.service;

import java.util.*;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.notification.model.EmailLog;
import com.FaceLit.backend.notification.repository.*;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.shared.service.UserDisplayNameService;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

/** App-only escalation. Never attempts to report a failed email by sending another email. */
@Service
public class EmailFailureDelivery {
    private final EmailLogRepository logs;
    private final NotificationRepository notices;
    private final UserRoleRepository roles;
    private final NotificationService delivery;
    private final UserDisplayNameService names;
    private final TransactionTemplate tx;
    public EmailFailureDelivery(EmailLogRepository logs, NotificationRepository notices, UserRoleRepository roles,
            NotificationService delivery, UserDisplayNameService names, TransactionTemplate tx) {
        this.logs = logs; this.notices = notices; this.roles = roles; this.delivery = delivery; this.names = names; this.tx = tx;
    }
    @Scheduled(fixedDelay = 60_000)
    public void scan() {
        for (var email : logs.findBySendStatus(EmailLog.SendStatus.FAILED)) {
            if (email.getAttempts() != null && email.getAttempts() >= 3) {
                try { deliver(email.getIdNotification()); }
                catch (RuntimeException error) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Aviso de fallo de correo pendiente {}", email.getIdNotification(), error); }
            }
        }
    }
    public void deliver(UUID originalId) {
        tx.executeWithoutResult(status -> {
            var email = logs.lockByNotification(originalId).orElse(null);
            if (email == null || email.getSendStatus() != EmailLog.SendStatus.FAILED || email.getAttempts() == null || email.getAttempts() < 3) return;
            var original = notices.findById(originalId).orElse(null);
            if (original == null || "email_delivery_failed".equals(original.getType())) return;
            Set<UUID> coordinators = new LinkedHashSet<>();
            roles.findByRoleName(RoleName.COORDINATOR).forEach(role -> coordinators.add(role.getUser().getIdUser()));
            // When a coordinator made the decision, that coordinator owns follow-up.
            if (original.getMetadataJson() != null) {
                try {
                    var meta = new ObjectMapper().readTree(original.getMetadataJson());
                    UUID resolver = UUID.fromString(meta.path("decidedBy").asText());
                    if (coordinators.contains(resolver)) coordinators.retainAll(Set.of(resolver));
                } catch (Exception ignored) { /* No assigned coordinator: notify the coordination team. */ }
            }
            var existing = notices.findByReferenceEntityAndReferenceId("failed_notification", originalId);
            String recipientName = names.fullName(original.getIdUserApp());
            var meta = new ObjectMapper().createObjectNode();
            meta.put("affectedUserName", recipientName); meta.put("originalTitle", original.getTitle());
            meta.put("originalType", original.getType()); meta.put("attemptCount", email.getAttempts());
            meta.put("originalNotificationId", originalId.toString());
            meta.put("recipientContext", "coordinator");
            for (UUID coordinator : coordinators) {
                if (existing.stream().anyMatch(n -> coordinator.equals(n.getIdUserApp()) && "email_delivery_failed".equals(n.getType()))) continue;
                meta.put("recipientName", names.fullName(coordinator));
                delivery.createForRecipient(new CreateNotificationRequestDTO(coordinator, "email_delivery_failed", "Correo no entregado",
                        "No fue posible entregar el correo «" + original.getTitle() + "» a " + recipientName
                                + " después de " + email.getAttempts() + " intentos. Recibes este aviso como coordinador encargado del seguimiento. La notificación de la app se conserva; lo que falló fue la entrega del correo. Revisa el correo de la cuenta y contacta al destinatario. El sistema no confirma la causa técnica exacta de la falta de entrega.",
                        originalId, "failed_notification", null, meta.toString()));
            }
        });
    }
}
