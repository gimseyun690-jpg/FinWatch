package com.finwatch.realtime;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/**
 * KIS calls its Korea-time 09:00-16:50 US session "US daytime trading".
 * It is a separate broker session, not NASDAQ pre-market.
 */
@Component
public class KisUsDaytimeSessionResolver {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(16, 50);

    public boolean isOpen(Instant now) {
        if (now == null) {
            return false;
        }
        var seoulTime = now.atZone(SEOUL);
        DayOfWeek day = seoulTime.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = seoulTime.toLocalTime();
        return !time.isBefore(OPEN) && time.isBefore(CLOSE);
    }
}
