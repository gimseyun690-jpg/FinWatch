package com.finwatch.realtime;

import java.util.List;

public record RealtimeSnapshot(
        List<LiveQuote> quotes,
        List<RealtimeProviderStatus> providers) {
}
