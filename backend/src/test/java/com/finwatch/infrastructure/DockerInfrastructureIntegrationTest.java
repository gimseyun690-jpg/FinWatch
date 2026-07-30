package com.finwatch.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.finwatch.fx.cache.FxRateCacheStore;
import com.finwatch.fx.cache.FxRateCacheValue;

@SpringBootTest(properties = {
        "app.realtime.enabled=false",
        "app.auth.demo-users-enabled=false"
})
@Testcontainers(disabledWithoutDocker = true, parallel = true)
class DockerInfrastructureIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("finwatch")
            .withUsername("finwatch")
            .withPassword("finwatch-test");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Autowired
    private FxRateCacheStore fxRateCacheStore;

    @Test
    void appliesAllFlywayMigrationsToPostgresql17() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.isValid(2)).isTrue();
        }

        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("20");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet indexes = statement.executeQuery("""
                        SELECT indexname
                        FROM pg_indexes
                        WHERE schemaname = current_schema()
                        """)) {
            java.util.Set<String> names = new java.util.HashSet<>();
            while (indexes.next()) names.add(indexes.getString("indexname"));
            assertThat(names).contains(
                    "idx_news_kind_published_id",
                    "idx_news_source_published_id",
                    "idx_news_published_id",
                    "idx_ai_analyses_news_feature_generated");
        }
    }

    @Test
    void roundTripsApplicationCacheValueAndAppliesTtlInRedis8() {
        String key = "smoke:fx:USD:KRW";
        Instant now = Instant.now();
        FxRateCacheValue expected = new FxRateCacheValue(
                "USD", "KRW", new BigDecimal("1380.25"), "MID", "TESTCONTAINERS",
                "OANDA:USD_KRW", now, now);

        fxRateCacheStore.put(key, expected, Duration.ofSeconds(30));

        assertThat(fxRateCacheStore.get(key)).contains(expected);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(30L);
        redisTemplate.delete(key);
    }

    @Test
    void contentFeedIndexSupportsOneHundredThousandRowsWithinTheWarmP95Target() throws Exception {
        String contentQuery = """
                SELECT id, title, published_at
                FROM news_articles
                WHERE content_kind = 'DISCLOSURE'
                ORDER BY published_at DESC, id DESC
                LIMIT 50
                """;
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM news_articles WHERE source = 'PERF'");
            try {
                statement.executeUpdate("""
                        INSERT INTO news_articles (
                            stock_id, external_id, title, publisher, url, canonical_url,
                            published_at, source, content_kind, disclosure_type,
                            content_source, rights_profile, created_at
                        )
                        SELECT
                            (SELECT id FROM stocks WHERE market = 'KRX' AND symbol = '000660'),
                            'content-feed-perf-' || series,
                            'Content feed performance fixture ' || series,
                            'FinWatch Performance Test',
                            'https://example.com/content-feed-perf/' || series,
                            'https://example.com/content-feed-perf/' || series,
                            TIMESTAMPTZ '2026-07-15 12:00:00+00' - (series * INTERVAL '1 second'),
                            'PERF',
                            CASE WHEN MOD(series, 100) = 0 THEN 'DISCLOSURE' ELSE 'NEWS' END,
                            CASE WHEN MOD(series, 100) = 0 THEN 'PERIODIC_REPORT' ELSE NULL END,
                            'METADATA_ONLY',
                            'METADATA_ONLY',
                            CURRENT_TIMESTAMP
                        FROM generate_series(1, 100000) AS series
                        """);
                statement.execute("ANALYZE news_articles");

                StringBuilder plan = new StringBuilder();
                try (ResultSet rows = statement.executeQuery("EXPLAIN (ANALYZE, BUFFERS) " + contentQuery)) {
                    while (rows.next()) plan.append(rows.getString(1)).append('\n');
                }
                assertThat(plan.toString()).contains("idx_news_kind_published_id");

                List<Long> elapsedMillis = new ArrayList<>();
                for (int run = 0; run < 20; run++) {
                    long started = System.nanoTime();
                    int rowCount = 0;
                    try (ResultSet rows = statement.executeQuery(contentQuery)) {
                        while (rows.next()) rowCount++;
                    }
                    elapsedMillis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                    assertThat(rowCount).isEqualTo(50);
                }
                Collections.sort(elapsedMillis);
                long p95Millis = elapsedMillis.get((int) Math.ceil(elapsedMillis.size() * 0.95) - 1);
                assertThat(p95Millis).isLessThanOrEqualTo(300L);
            } finally {
                statement.executeUpdate("DELETE FROM news_articles WHERE source = 'PERF'");
            }
        }
    }
}
