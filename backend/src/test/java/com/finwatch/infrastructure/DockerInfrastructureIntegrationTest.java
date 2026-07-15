package com.finwatch.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("17");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
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
}
