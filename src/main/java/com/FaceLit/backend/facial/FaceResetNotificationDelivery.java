package com.FaceLit.backend.facial;

import java.util.Map;
import java.util.UUID;
import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.facial.repository.FaceResetRequestRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.repository.NotificationRepository;
import com.FaceLit.backend.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Persistent outbox: decision and authorization survive notification/SMTP failures. */
@Component
public class FaceResetNotificationDelivery {
    private static final Logger LOG = LoggerFactory.getLogger(FaceResetNotificationDelivery.class);
    private final FaceResetRequestRepository requests;
    private final NotificationRepository notifications;
    private final UserRoleRepository roles;
    private final NotificationService service;
    private final FaceResetService reset;
    private final TransactionTemplate transactions;
    private final String appUrl;

    public FaceResetNotificationDelivery(FaceResetRequestRepository requests, NotificationRepository notifications,
            UserRoleRepository roles, NotificationService service, FaceResetService reset, TransactionTemplate transactions,
            @Value("${facelit.access-url:http://localhost:8081}") String appUrl) {
        this.requests = requests; this.notifications = notifications; this.roles = roles;
        this.service = service; this.reset = reset; this.transactions = transactions;
        this.appUrl = appUrl.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString = "${facial.reset.notification-retry-ms:5000}")
    public void retryPending() {
        var pending = new java.util.LinkedHashSet<>(requests.findPendingNotifications());
        pending.addAll(requests.findUnnotifiedExpired(java.time.OffsetDateTime.now()));
        for (UUID id : pending) {
            try { deliver(id); }
            catch (RuntimeException ex) { LOG.warn("Entrega facial pendiente; se reintentará. request={}", id, ex); }
        }
    }

    public void deliver(UUID id) {
        transactions.executeWithoutResult(tx -> {
            var request = requests.lockById(id).orElseThrow();
            Map<String, Object> context = reset.readJson(request.getContextJson());
            String details = "\nAprendiz: " + context.get("learnerName")
                    + "\nFicha: " + context.get("fichaNumber") + "\nPrograma: " + context.get("programName");
            if (!request.isRequestNotified()) {
                var coordinators = roles.findByRoleName(RoleName.COORDINATOR);
                if (coordinators.isEmpty()) throw new IllegalStateException("No hay coordinadores para recibir la solicitud.");
                for (var coordinator : coordinators) {
                    send(request.getId(), coordinator.getUser().getIdUser(), "facial_reregister_request",
                            "Solicitud de restablecimiento del registro facial",
                            "El aprendiz solicita autorización para volver a registrar su rostro y reemplazar el embedding anterior."
                                    + details + "\nAbre la app para aceptar o rechazar: " + appUrl + "/notifications", context);
                }
                request.setRequestNotified(true);
            }
            if (!"PENDING".equals(request.getStatus()) && !request.isDecisionNotified()) {
                boolean accepted = "ACCEPTED".equals(request.getStatus());
                context.put("facialDecision", accepted ? "accepted" : "rejected");
                context.put("decidedBy", request.getResolvedBy().toString());
                String decidedByName = context.get("decidedByName") instanceof String name ? name : "";
                if (decidedByName.isBlank()) decidedByName = reset.coordinatorName(request.getResolvedBy());
                context.put("decidedByName", decidedByName);
                context.put("decidedAt", request.getResolvedAt().toString());
                if (accepted) context.put("authorizationExpiresAt", request.getAuthorizationExpiresAt().toString());
                send(request.getId(), request.getUserId(), "facial_request_resolved",
                        accepted ? "Registro facial autorizado" : "Solicitud facial rechazada",
                        (accepted ? "El coordinador aprobó tu solicitud. Puedes abrir la cámara y registrar tu rostro una sola vez hasta "
                                + request.getAuthorizationExpiresAt() + ". El nuevo rostro reemplazará al anterior al completar el registro."
                                : "El coordinador rechazó tu solicitud. Tu registro facial anterior se conserva y puedes solicitar nuevamente.")
                                + details + "\nDecidido por: " + (decidedByName.isBlank() ? "Coordinador" : decidedByName)
                                + "\nAbre Mi reconocimiento facial: " + appUrl + "/apprentice/facial", context);
                request.setDecisionNotified(true);
            }
            if (request.getConsumedAt() != null && !request.isCompletionNotified()) {
                send(request.getId(), request.getUserId(), "facial_registration_completed", "Registro facial reemplazado",
                        "Tu nuevo rostro fue registrado. El embedding anterior fue reemplazado y la autorización quedó consumida."
                                + details, context);
                request.setCompletionNotified(true);
            }
            if ("ACCEPTED".equals(request.getStatus()) && request.getConsumedAt() == null
                    && request.getAuthorizationExpiresAt() != null
                    && !request.getAuthorizationExpiresAt().isAfter(java.time.OffsetDateTime.now())) {
                context.put("authorizationExpiresAt", request.getAuthorizationExpiresAt().toString());
                context.put("recipientName", context.get("learnerName")); context.put("recipientContext", "learner");
                send(request.getId(), request.getUserId(), "facial_authorization_expired", "Permiso facial vencido",
                        "Tu permiso de un solo uso para reemplazar el rostro venció sin utilizarse el "
                                + request.getAuthorizationExpiresAt().atZoneSameInstant(java.time.ZoneId.of("America/Bogota"))
                                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                                + " (hora de Colombia). Recibes este aviso porque el plazo de tu autorización terminó. Tu rostro anterior se conserva y la cámara permanece bloqueada. Solicita nuevamente autorización al coordinador."
                                + details + "\nAbre Mi reconocimiento facial: " + appUrl + "/apprentice/facial", context);
            }
            requests.save(request);
        });
    }

    private void send(UUID id, UUID userId, String type, String title, String message, Map<String, Object> context) {
        boolean exists = notifications.findByReferenceEntityAndReferenceId("face_reset_request", id).stream()
                .anyMatch(n -> userId.equals(n.getIdUserApp()) && type.equals(n.getType()));
        if (!exists) service.createForRecipient(new CreateNotificationRequestDTO(userId, type, title, message,
                id, "face_reset_request", null, reset.writeJson(context)));
    }
}
