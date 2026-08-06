package com.finwatch.realtime;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

@Component
public class UsMarketSessionResolver {

    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static final LocalTime PRE_MARKET_OPEN = LocalTime.of(4, 0);
    private static final LocalTime REGULAR_OPEN = LocalTime.of(9, 30);
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime AFTER_HOURS_CLOSE = LocalTime.of(20, 0);

    public MarketSessionStatus resolve(Instant tradeTime) {
        if (tradeTime == null) {
            return MarketSessionStatus.UNKNOWN;
        }

        var newYorkTime = tradeTime.atZone(NEW_YORK);
        DayOfWeek day = newYorkTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return MarketSessionStatus.CLOSED;
        }

        LocalTime time = newYorkTime.toLocalTime();
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
