package com.finwatch.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Parses the 25-field KIS HDFSCNT0 overseas trade frame. */
public final class KisOverseasTradeMessageParser {

    static final String TRANSACTION_ID = "HDFSCNT0";
    static final int FIELD_COUNT = 25;
    private static final int SYMBOL = 0;
    private static final int KOREA_DATE = 5;
    private static final int KOREA_TIME = 6;
    private static final int LAST = 10;
    private static final int SIGN = 11;
    private static final int DIFFERENCE = 12;
    private static final int RATE = 13;
    private static final int TOTAL_VOLUME = 19;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    private KisOverseasTradeMessageParser() {
    }

    public static List<KisOverseasTrade> parse(String message, Instant receivedAt) {
        if (message == null || !message.startsWith("0|")) {
            return List.of();
        }
        String[] envelope = message.split("\\|", 4);
        if (envelope.length != 4 || !TRANSACTION_ID.equals(envelope[1])) {
            return List.of();
        }
        int expected = integer(envelope[2], 1);
        String[] values = envelope[3].split("\\^", -1);
        int available = values.length / FIELD_COUNT;
        int count = Math.min(Math.max(expected, 1), available);
        List<KisOverseasTrade> trades = new ArrayList<>(count);
        for (int record = 0; record < count; record++) {
            int offset = record * FIELD_COUNT;
            String symbol = values[offset + SYMBOL].trim();
            BigDecimal last = decimal(values[offset + LAST]);
            if (symbol.isBlank() || last.signum() <= 0) {
                continue;
            }
            String sign = values[offset + SIGN].trim();
            Timestamp timestamp = timestamp(values[offset + KOREA_DATE], values[offset + KOREA_TIME], receivedAt);
            trades.add(new KisOverseasTrade(
                    symbol,
                    last,
                    signed(decimal(values[offset + DIFFERENCE]), sign),
                    signed(decimal(values[offset + RATE]), sign),
                    decimal(values[offset + TOTAL_VOLUME]),
                    timestamp.asOf(),
                    timestamp.providerTimestamp()));
        }
        return List.copyOf(trades);
    }

    private static BigDecimal signed(BigDecimal value, String sign) {
        return ("4".equals(sign) || "5".equals(sign)) && value.signum() > 0 ? value.negate() : value;
    }

    private static Timestamp timestamp(String date, String time, Instant fallback) {
        try {
            return new Timestamp(LocalDateTime.of(
                    LocalDate.parse(date.trim(), DATE),
                    LocalTime.parse(time.trim(), TIME))
                    .atZone(SEOUL)
                    .toInstant(), true);
        } catch (DateTimeParseException ignored) {
            return new Timestamp(fallback == null ? Instant.EPOCH : fallback, false);
        }
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (RuntimeException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public record KisOverseasTrade(
            String providerSymbol,
            BigDecimal price,
            BigDecimal change,
            BigDecimal changeRate,
            BigDecimal volume,
            Instant asOf,
            boolean providerTimestamp) {
    }

    private record Timestamp(Instant asOf, boolean providerTimestamp) {
    }
}
