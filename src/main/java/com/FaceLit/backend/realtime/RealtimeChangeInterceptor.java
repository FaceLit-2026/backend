package com.FaceLit.backend.realtime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RealtimeChangeInterceptor implements HandlerInterceptor {

    private final RealtimeWebSocketHandler publisher;

    public RealtimeChangeInterceptor(RealtimeWebSocketHandler publisher) {
        this.publisher = publisher;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            @Nullable Exception exception) {
        String method = request.getMethod();
        if (exception != null || response.getStatus() < 200 || response.getStatus() >= 300
                || "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return;
        }

        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.startsWith("/api/auth/")) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return;
        }

        publisher.publish(
                new RealtimeChange("data.changed", resourceFor(path), actionFor(method),
                        authentication.getName(), Instant.now()),
                path);
    }

    private String actionFor(String method) {
        return switch (method) {
            case "POST" -> "created";
            case "DELETE" -> "deleted";
            default -> "updated";
        };
    }

    private String resourceFor(String path) {
        if (path.startsWith("/api/profile/configuration")) return "user-configuration";
        if (path.startsWith("/api/notifications")) return "notifications";
        if (path.startsWith("/api/attendance")) return "attendance";
        if (path.startsWith("/api/admin/users")) return "users";
        if (path.startsWith("/api/admin")) return "roles";
        if (path.startsWith("/api/academic")) return "academic";
        if (path.startsWith("/api/environment")) return "environment";
        if (path.startsWith("/api/facial")) return "facial";
        return "data";
    }
}
