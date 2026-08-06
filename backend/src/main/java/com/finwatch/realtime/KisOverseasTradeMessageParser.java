package com.finwatch.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses KIS overseas stock realtime trade messages (TR_ID: HDFSCNT0).
 *
 * <p>The wire format is: {@code 0|HDFSCNT0|recordCount|field0^field1^...^fieldN}
 * where each record consists of 25 caret-delimited fields.
 *
 * <p>Key field indices (0-based within a record):
 * <pre>
 *   0  RSYM   – realtime symbol (e.g. "D+NAS+AAPL")
 *   1  SYMB   – symbol code
 *   2  ZDIV   – decimal precision
 *   3  TYMD   – business date
 *   4  XYMD   – local date (yyyyMMdd)
 *   5  XHMS   – local time (HHmmss)
 *   6  KYMD   – Korea date (yyyyMMdd)
 *   7  KHMS   – Korea time (HHmmss)
 *   8  OPEN   – open price
 *   9  HIGH   – high price
 *  10  LOW    – low price
 *  11  LAST   – last (current) price
 *  12  SIGN   – change sign ("1"~"3" positive, "4"~"5" negative)
 *  13  DIFF   – change amount
 *  14  RATE   – change rate (%)
 *  15  PVOL   – previous day volume
 *  16  TVOL   – cumulative volume
 *  17  TAMT   – cumulative trade amount
 *  18  PBID   – bid price
 *  19  PASK   – ask price
 *  20  VBID   – bid volume
 *  21  VASK   – ask volume
 *  22  EVOL   – execution volume (tick volume)
 *  23  CGUB   – trade direction
 *  24  MDTM   – execution timestamp (ms)
 * </pre>
 */
public final class KisOverseasTradeMessageParser {

    static final int FIELD_COUNT = 25;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");
    private static final UsMarketSessionResolver SESSION_RESOLVER = new UsMarketSessionResolver();

    private KisOverseasTradeMessageParser() {
    }

    /**
     * Parses a raw KIS overseas trade WebSocket message into a list of {@link LiveQuote}.
     *
     * @param message raw pipe-delimited message starting with "0|HDFSCNT0|..."
     * @return list of parsed quotes; empty if the message cannot be parsed
     */
    public static List<LiveQuote> parse(String message) {
        if (message == null || message.isEmpty()) {
            return List.of();
        }
        String[] envelope = message.split("\\|", 4);
        if (envelope.length < 4 || !"HDFSCNT0".equals(envelope[1])) {
            return List.of();
        }

        String body = envelope[3];
        String[] fields = body.split("\\^", -1);
        int recordCount = Math.max(1, safeInt(envelope[2], 1));
        List<LiveQuote> quotes = new ArrayList<>(recordCount);

        for (int record = 0; record < recordCount; record++) {
            int offset = record * FIELD_COUNT;
            if (offset + FIELD_COUNT > fields.length) {
                break;
            }
            try {
                LiveQuote quote = parseRecord(fields, offset);
                if (quote != null) {
                    quotes.add(quote);
                }
            } catch (RuntimeException ignored) {
                // Skip malformed records
            }
        }
        return quotes;
    }

    private static LiveQuote parseRecord(String[] fields, int offset) {
        String rsym = fields[offset];          // RSYM: e.g. "D+NAS+AAPL"
        String symbol = fields[offset + 1];    // SYMB
        BigDecimal price = decimal(fields[offset + 11]);  // LAST
        if (price.signum() <= 0) {
            return null;
        }

        String signCode = fields[offset + 12]; // SIGN
        boolean negative = "4".equals(signCode) || "5".equals(signCode);

        BigDecimal change = decimal(fields[offset + 13]);  // DIFF
        if (negative && change.signum() > 0) {
            change = change.negate();
        }

        BigDecimal changeRate = decimal(fields[offset + 14]); // RATE
        if (negative && changeRate.signum() > 0) {
            changeRate = changeRate.negate();
        }

        BigDecimal volume = decimal(fields[offset + 16]); // TVOL

        // Resolve market from RSYM (e.g. "D+NAS+AAPL" or "DNASAAPL")
        String market = resolveMarket(rsym, symbol);

        // Resolve timestamp from Korea date/time (indices 6, 7)
        Instant asOf = resolveTimestamp(fields[offset + 6], fields[offset + 7]);

        // Resolve session status
        String sessionStatus = SESSION_RESOLVER.resolve(asOf).name();

        return new LiveQuote(
                market,
                symbol.trim().toUpperCase(Locale.ROOT),
                price,
                change,
                changeRate,
                volume,
                "USD",
                asOf,
                "KIS_OVERSEAS_WS",
                sessionStatus);
    }

    static String resolveMarket(String rsym, String symbol) {
        if (rsym == null || rsym.isBlank()) {
            return "UNKNOWN";
        }
        // Format can be "D+NAS+AAPL" or "DNASAAPL"
        String upper = rsym.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("NAS")) {
            return "NASDAQ";
        }
        if (upper.contains("NYS")) {
            return "NYSE";
        }
        if (upper.contains("AMS") || upper.contains("AMX")) {
            return "AMEX";
        }
        return "UNKNOWN";
    }

    private static Instant resolveTimestamp(String koreaDate, String koreaTime) {
        try {
            LocalDate date = LocalDate.parse(koreaDate.trim(), DATE);
            LocalTime time = LocalTime.parse(koreaTime.trim(), TIME);
            return ZonedDateTime.of(date, time, SEOUL).toInstant();
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static int safeInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
