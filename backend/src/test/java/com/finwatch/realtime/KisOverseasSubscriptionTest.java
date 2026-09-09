package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KisOverseasSubscriptionTest {

    @Test
    void buildsStandardKeysWithoutGuessingTheMarket() {
        assertThat(KisOverseasSubscription.standard("nasdaq", "aapl").trKey()).isEqualTo("DNASAAPL");
        assertThat(KisOverseasSubscription.standard("nyse", "brk.b").trKey()).isEqualTo("DNYSBRK.B");
    }
}
