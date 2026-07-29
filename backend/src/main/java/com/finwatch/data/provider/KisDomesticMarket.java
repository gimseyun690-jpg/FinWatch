package com.finwatch.data.provider;

import java.util.Arrays;
import java.util.Locale;

/**
 * KIS domestic quote contracts.
 *
 * <p>NXT trades the same listed securities as KRX, so FinWatch keeps the
 * canonical instrument market as KRX and records the execution venue in the
 * provider source.</p>
 */
public enum KisDomesticMarket {

    KRX("J", "H0STCNT0", "KIS_KRX", "KRX"),
    NXT("NX", "H0NXCNT0", "KIS_NXT", "NXT"),
    UNIFIED("UN", "H0UNCNT0", "KIS_UNIFIED", "KRX+NXT 통합");

    private final String restCode;
    private final String realtimeTrId;
    private final String sourcePrefix;
    private final String displayName;

    KisDomesticMarket(String restCode, String realtimeTrId, String sourcePrefix, String displayName) {
        this.restCode = restCode;
        this.realtimeTrId = realtimeTrId;
        this.sourcePrefix = sourcePrefix;
        this.displayName = displayName;
    }

    public String restCode() {
        return restCode;
    }

    public String realtimeTrId() {
        return realtimeTrId;
    }

    public String providerId() {
        return sourcePrefix.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public String restSource() {
        return sourcePrefix + "_REST";
    }

    public String websocketSource() {
        return sourcePrefix + "_WS";
    }

    public String persistenceSource() {
        return sourcePrefix;
    }

    public String displayName() {
        return displayName;
    }

    public boolean includesNxt() {
        return this == NXT || this == UNIFIED;
    }

    public static KisDomesticMarket fromCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(market -> market.restCode.equals(normalized) || market.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "KIS_DOMESTIC_MARKET_CODE must be one of J, NX, UN, KRX, NXT, UNIFIED."));
    }

    public static KisDomesticMarket fromRealtimeTrId(String trId) {
        return Arrays.stream(values())
                .filter(market -> market.realtimeTrId.equals(trId))
                .findFirst()
                .orElse(null);
    }
}
