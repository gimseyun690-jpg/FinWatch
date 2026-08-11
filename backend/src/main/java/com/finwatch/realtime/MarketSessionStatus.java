package com.finwatch.realtime;

import java.util.Locale;

public enum MarketSessionStatus {
    LIVE(true),
    PRE_MARKET(true),
    REGULAR(true),
    AFTER_HOURS(true),
    US_DAYTIME(true),
    CLOSED(false),
    SNAPSHOT(false),
    UNKNOWN(false);

    private final boolean streaming;

    MarketSessionStatus(boolean streaming) {
        this.streaming = streaming;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public static boolean isStreaming(String value) {
        try {
            return value != null && valueOf(value.trim().toUpperCase(Locale.ROOT)).isStreaming();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
