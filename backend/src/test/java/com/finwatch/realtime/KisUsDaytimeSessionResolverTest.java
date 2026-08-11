package com.finwatch.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class KisUsDaytimeSessionResolverTest {

    private final KisUsDaytimeSessionResolver resolver = new KisUsDaytimeSessionResolver();

    @Test
    void usesKoreaTimeForTheKisUsDaytimeSession() {
        assertThat(resolver.isOpen(Instant.parse("2026-08-10T23:59:59Z"))).isFalse();
        assertThat(resolver.isOpen(Instant.parse("2026-08-11T00:00:00Z"))).isTrue();
        assertThat(resolver.isOpen(Instant.parse("2026-08-11T07:49:59Z"))).isTrue();
        assertThat(resolver.isOpen(Instant.parse("2026-08-11T07:50:00Z"))).isFalse();
    }

    @Test
    void doesNotPlanDaytimeSubscriptionsOnWeekends() {
        assertThat(resolver.isOpen(Instant.parse("2026-08-15T03:00:00Z"))).isFalse();
    }
}
