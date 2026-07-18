package com.finwatch.realtime;

import java.time.Instant;

public record RealtimeProviderStatus(
        String provider,
        String state,
        String message,
        Instant updatedAt) {
}
