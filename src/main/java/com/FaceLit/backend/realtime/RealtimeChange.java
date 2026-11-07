package com.FaceLit.backend.realtime;

import java.time.Instant;

public record RealtimeChange(
        String type,
        String resource,
        String action,
        String actorId,
        Instant changedAt) {
}
