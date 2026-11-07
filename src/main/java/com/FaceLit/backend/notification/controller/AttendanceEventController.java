package com.FaceLit.backend.notification.controller;

import com.FaceLit.backend.notification.dto.request.MonolithEventRequestDTO;
import com.FaceLit.backend.notification.dto.response.NotificationResponseDTO;
import com.FaceLit.backend.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance/events")
public class AttendanceEventController {

    private final NotificationService notificationService;

    public AttendanceEventController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<List<NotificationResponseDTO>> receiveMicroEvent(
            @Valid @RequestBody MonolithEventRequestDTO request) {
        return ResponseEntity.ok(notificationService.createCoordinatorEvent(request));
    }
}
