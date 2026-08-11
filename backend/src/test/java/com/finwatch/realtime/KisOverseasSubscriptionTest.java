package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KisOverseasSubscriptionTest {

    @Test
    void buildsNormalAndDaytimeKeysWithoutGuessingTheMarket() {
        assertThat(KisOverseasSubscription.standard("nasdaq", "aapl").trKey()).isEqualTo("DNASAAPL");
        assertThat(KisOverseasSubscription.daytime("nasdaq", "aapl").trKey()).isEqualTo("RBAQAAPL");
        assertThat(KisOverseasSubscription.daytime("nyse", "brk.b").trKey()).isEqualTo("RBAYBRK.B");
    }
}
