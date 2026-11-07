package com.FaceLit.backend.facial.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.FaceLit.backend.facial.repository.FacialRegistrationStatusRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.service.NotificationService;

/** Application rules for registration status and idempotent completion notices. */
@Service
public class FacialRegistrationService {
    private final FacialRegistrationStatusRepository repository;
    private final TransactionTemplate transactions;
    private final NotificationService notificationService;

    public FacialRegistrationService(FacialRegistrationStatusRepository repository,
            TransactionTemplate transactions, NotificationService notificationService) {
        this.repository = repository;
        this.transactions = transactions;
        this.notificationService = notificationService;
    }

    public record RegistrationStatus(boolean registered, OffsetDateTime registrationDate) {}

    public boolean isRegistered(UUID userId) {
        return repository.registered(userId);
    }

    public RegistrationStatus status(UUID userId) {
        OffsetDateTime date = repository.registrationDate(userId);
        return new RegistrationStatus(date != null, date);
    }

    public Map<String, Boolean> confirm(UUID userId) {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        transactions.executeWithoutResult(tx -> {
            repository.lockUser(userId);
            if (!repository.registered(userId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay registro facial guardado.");
            boolean notified = repository.completionNotified(userId);
            boolean replacement = repository.replacementConsumed(userId);
            if (!notified && !replacement) notificationService.createForRecipient(new CreateNotificationRequestDTO(
                    userId, "facial_registration_completed", "Registro facial completado",
                    "Tu rostro se guardó correctamente. Para reemplazarlo debes solicitar autorización al coordinador.",
                    userId, "user_face", null, "{\"action\":\"completed\"}"));
        });
        return Map.of("confirmed", true);
    }
}
