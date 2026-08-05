package com.finwatch.realtime;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

@Component
public class KrxMarketSessionResolver {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final LocalTime PRE_MARKET_OPEN = LocalTime.of(8, 30);
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 0);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime AFTER_HOURS_CLOSE = LocalTime.of(18, 0);

    public MarketSessionStatus resolve(Instant tradeTime) {
        if (tradeTime == null) {
            return MarketSessionStatus.UNKNOWN;
        }

        var seoulTime = tradeTime.atZone(SEOUL);
        DayOfWeek day = seoulTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return MarketSessionStatus.CLOSED;
        }

        LocalTime time = seoulTime.toLocalTime();
        if (!time.isBefore(PRE_MARKET_OPEN) && time.isBefore(REGULAR_OPEN)) {
            return MarketSessionStatus.PRE_MARKET;
        }
        if (!time.isBefore(REGULAR_OPEN) && time.isBefore(REGULAR_CLOSE)) {
            return MarketSessionStatus.REGULAR;
        }
        if (!time.isBefore(REGULAR_CLOSE) && time.isBefore(AFTER_HOURS_CLOSE)) {
            return MarketSessionStatus.AFTER_HOURS;
        }
        return MarketSessionStatus.CLOSED;
    }
}
