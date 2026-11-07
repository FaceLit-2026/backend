package com.FaceLit.backend.notification.service.serviceImpl;

import com.FaceLit.backend.auth.model.enums.RoleName;
import com.FaceLit.backend.auth.model.roleandpermission.UserRole;
import com.FaceLit.backend.auth.repository.roleandpermission.UserRoleRepository;
import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.dto.response.NotificationResponseDTO;
import com.FaceLit.backend.notification.model.EmailLog;
import com.FaceLit.backend.notification.model.Notification;
import com.FaceLit.backend.notification.repository.EmailLogRepository;
import com.FaceLit.backend.notification.repository.NotificationRepository;
import com.FaceLit.backend.notification.service.EmailDispatchRequested;
import com.FaceLit.backend.notification.service.NotificationCatalog;
import com.FaceLit.backend.notification.service.NotificationService;
import com.FaceLit.backend.shared.service.UserDisplayNameService;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int NOTIFICATION_RETENTION_DAYS = 30;

    private final NotificationRepository notificationRepository;
    private final EmailLogRepository emailLogRepository;
    private final UserRoleRepository userRoleRepository;
    private final NotificationCatalog catalog;
    private final ApplicationEventPublisher eventPublisher;
    private final UserDisplayNameService names;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            EmailLogRepository emailLogRepository,
            UserRoleRepository userRoleRepository,
            NotificationCatalog catalog,
            ApplicationEventPublisher eventPublisher, UserDisplayNameService names) {
        this.notificationRepository = notificationRepository;
        this.emailLogRepository = emailLogRepository;
        this.userRoleRepository = userRoleRepository;
        this.catalog = catalog;
        this.eventPublisher = eventPublisher;
        this.names = names;
    }

    @Transactional(readOnly = true)
    @Override
    public List<NotificationResponseDTO> findForUser(UUID userId) {
        return notificationRepository.findByIdUserAppAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                userId, OffsetDateTime.now().minusDays(NOTIFICATION_RETENTION_DAYS))
                .stream()
                .filter(notification -> !"EMAIL".equals(notification.getChannel())
                        && !"attendance_exit_reminder".equals(notification.getType()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public NotificationResponseDTO markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notificacion no encontrada"));
        if (!notification.getIdUserApp().equals(userId)) {
            throw new IllegalArgumentException("No puede modificar esta notificacion");
        }
        notification.markRead();
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    @Override
    public List<NotificationResponseDTO> markAllRead(UUID userId) {
        List<Notification> notifications = notificationRepository
                .findByIdUserAppAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        userId, OffsetDateTime.now().minusDays(NOTIFICATION_RETENTION_DAYS)).stream()
                .filter(notification -> !"EMAIL".equals(notification.getChannel())
                        && !"attendance_exit_reminder".equals(notification.getType())).toList();
        notifications.forEach(Notification::markRead);
        return notificationRepository.saveAll(notifications).stream().map(this::toResponse).toList();
    }

    @Transactional
    @Override
    public NotificationResponseDTO createForRecipient(CreateNotificationRequestDTO request) {
        return toResponse(createNotification(
                request.recipientUserId(),
                request.type(),
                request.title(),
                request.message(),
                request.referenceId(),
                request.referenceEntity(),
                request.facialEventId(),
                request.metadataJson()));
    }

    @Transactional
    @Override
    public List<NotificationResponseDTO> createCoordinatorEvent(MonolithEventRequestDTO request) {
        if (request.recipientUserId() != null) {
            return List.of(toResponse(createNotification(
                    request.recipientUserId(),
                    request.type(),
                    request.title(),
                    request.message(),
                    request.referenceId(),
                    request.referenceEntity(),
                    request.facialEventId(),
                    request.metadataJson())));
        }

        return userRoleRepository.findByRoleName(RoleName.COORDINATOR)
                .stream()
                .map(UserRole::getUser)
                .map(user -> createNotification(
                        user.getIdUser(),
                        request.type(),
                        request.title(),
                        request.message(),
                        request.referenceId(),
                        request.referenceEntity(),
                        request.facialEventId(),
                        request.metadataJson()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public List<NotificationResponseDTO> createIndependentCoordinatorEvent(MonolithEventRequestDTO request) {
        return createCoordinatorEvent(request);
    }

    private Notification createNotification(
            UUID recipientId,
            String type,
            String title,
            String message,
            UUID referenceId,
            String referenceEntity,
            UUID facialEventId,
            String metadataJson) {
        NotificationCatalog.NotificationDefinition definition = catalog.require(type);
        Notification notification = new Notification();
        notification.setIdUserApp(recipientId);
        notification.setType(type);
        notification.setCategory(definition.category());
        notification.setChannel(definition.channel());
        notification.setTitle((title == null || title.isBlank()) ? definition.defaultTitle() : title);
        notification.setMessage(message);
        notification.setReferenceId(referenceId);
        notification.setReferenceEntity(referenceEntity);
        notification.setIdFacialEvent(facialEventId);
        notification.setMetadataJson(metadataJson);

        Notification saved = notificationRepository.save(notification);
        if ("APP_EMAIL".equals(definition.channel()) || "EMAIL".equals(definition.channel())) {
            EmailLog emailLog = new EmailLog();
            emailLog.setIdNotification(saved.getIdNotification());
            emailLogRepository.save(emailLog);
            eventPublisher.publishEvent(new EmailDispatchRequested(saved.getIdNotification()));
        }
        return saved;
    }

    private NotificationResponseDTO toResponse(Notification notification) {
        String emailStatus = emailLogRepository.findByIdNotification(notification.getIdNotification())
                .map(log -> log.getSendStatus().name())
                .orElse(null);
        return new NotificationResponseDTO(
                notification.getIdNotification(),
                notification.getIdUserApp(),
                notification.getType(),
                notification.getCategory(),
                notification.getChannel(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.isReadStatus(),
                displayMetadata(notification),
                emailStatus);
    }

    private String displayMetadata(Notification notification) {
        String original = notification.getMetadataJson();
        if (original == null || !("facial_request_resolved".equals(notification.getType())
                || "facial_reregister_request".equals(notification.getType()))) return original;
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var metadata = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(original);
            if (metadata.path("decidedByName").asText().isBlank() && metadata.hasNonNull("decidedBy")) {
                String name = names.fullName(UUID.fromString(metadata.get("decidedBy").asText()));
                if (name != null && !name.isBlank()) metadata.put("decidedByName", name);
            }
            return mapper.writeValueAsString(metadata);
        } catch (Exception ignored) { return original; }
    }
}
