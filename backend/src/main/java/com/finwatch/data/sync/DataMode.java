package com.finwatch.data.sync;

import java.util.Locale;

public enum DataMode {
    DEMO,
    LIVE;

    public static DataMode from(String value) {
        try {
            return DataMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("DATA_MODE must be DEMO or LIVE.", exception);
        }
    }
}
