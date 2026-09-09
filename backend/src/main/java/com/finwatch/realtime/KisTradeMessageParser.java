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

public final class KisTradeMessageParser {

    private static final int FIELD_COUNT = 46;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    private KisTradeMessageParser() {
    }

    public static List<LiveQuote> parse(String message) {
        if (message == null || !message.startsWith("0|")) {
            return List.of();
        }
        String[] envelope = message.split("\\|", 4);
        if (envelope.length != 4) {
            return List.of();
        }
        var domesticMarket = com.finwatch.data.provider.KisDomesticMarket.fromRealtimeTrId(envelope[1]);
        if (domesticMarket == null) {
            return List.of();
        }
        int fieldCount = FIELD_COUNT;
        int recordCount = integer(envelope[2], 1);
        String[] values = envelope[3].split("\\^", -1);
        int availableRecords = values.length / fieldCount;
        int count = Math.min(Math.max(recordCount, 1), availableRecords);
        List<LiveQuote> quotes = new ArrayList<>(count);
        for (int record = 0; record < count; record++) {
            int offset = record * fieldCount;
            String symbol = values[offset].trim();
            BigDecimal price = decimal(values[offset + 2]);
            if (symbol.isBlank() || price.signum() <= 0) {
                continue;
            }
            String signCode = values[offset + 3].trim();
            BigDecimal change = signed(decimal(values[offset + 4]), signCode);
            BigDecimal changeRate = signed(decimal(values[offset + 5]), signCode);
            Instant tradeTime = timestamp(values[offset + 33], values[offset + 1]);
            MarketSessionStatus sessionStatus = SESSION_RESOLVER.resolve(tradeTime);
            quotes.add(new LiveQuote(
                    "KRX",
                    symbol,
                    price,
                    change,
                    changeRate,
                    decimal(values[offset + 13]),
                    "KRW",
                    tradeTime,
                    domesticMarket.websocketSource(),
                    sessionStatus.name()));
        }
        return List.copyOf(quotes);
    }

    private static final KrxMarketSessionResolver SESSION_RESOLVER = new KrxMarketSessionResolver();

    private static BigDecimal signed(BigDecimal value, String signCode) {
        if (("4".equals(signCode) || "5".equals(signCode)) && value.signum() > 0) {
            return value.negate();
        }
        return value;
    }

    private static Instant timestamp(String date, String time) {
        try {
            return LocalDateTime.of(
                    LocalDate.parse(date.trim(), DATE),
                    LocalTime.parse(time.trim(), TIME))
                    .atZone(SEOUL)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            return Instant.now();
        }
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value.trim());
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
}
