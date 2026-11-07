package com.FaceLit.backend.notification.service;

import com.FaceLit.backend.notification.dto.request.CreateNotificationRequestDTO;
import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.dto.response.NotificationResponseDTO;
import java.util.List;
import java.util.UUID;

public interface NotificationService {
    List<NotificationResponseDTO> findForUser(UUID userId);

    NotificationResponseDTO markRead(UUID userId, UUID notificationId);

    List<NotificationResponseDTO> markAllRead(UUID userId);

    NotificationResponseDTO createForRecipient(CreateNotificationRequestDTO request);

    List<NotificationResponseDTO> createCoordinatorEvent(MonolithEventRequestDTO request);

    List<NotificationResponseDTO> createIndependentCoordinatorEvent(MonolithEventRequestDTO request);
}
