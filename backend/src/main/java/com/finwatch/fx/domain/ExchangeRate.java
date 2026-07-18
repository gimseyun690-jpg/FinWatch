package com.finwatch.fx.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "exchange_rates", uniqueConstraints = @UniqueConstraint(
        name = "uk_exchange_rates_pair_source_time",
        columnNames = {"base_currency", "quote_currency", "source", "as_of"}))
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    @Column(nullable = false, precision = 24, scale = 10)
    private BigDecimal rate;

    @Column(name = "open_rate", precision = 24, scale = 10)
    private BigDecimal openRate;

    @Column(name = "high_rate", precision = 24, scale = 10)
    private BigDecimal highRate;

    @Column(name = "low_rate", precision = 24, scale = 10)
    private BigDecimal lowRate;

    @Column(name = "close_rate", precision = 24, scale = 10)
    private BigDecimal closeRate;

    @Column(name = "rate_type", nullable = false, length = 20)
    private String rateType;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "provider_symbol", length = 100)
    private String providerSymbol;

    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExchangeRate() {
    }

    public static ExchangeRate create(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal rate,
            BigDecimal openRate,
            BigDecimal highRate,
            BigDecimal lowRate,
            BigDecimal closeRate,
            String rateType,
            String source,
            String providerSymbol,
            Instant asOf,
            Instant fetchedAt) {
        ExchangeRate value = new ExchangeRate();
        value.baseCurrency = baseCurrency;
        value.quoteCurrency = quoteCurrency;
        value.rate = rate;
        value.openRate = openRate;
        value.highRate = highRate;
        value.lowRate = lowRate;
        value.closeRate = closeRate;
        value.rateType = rateType;
        value.source = source;
        value.providerSymbol = providerSymbol;
        value.asOf = asOf;
        value.fetchedAt = fetchedAt;
        value.createdAt = Instant.now();
        return value;
    }

    public Long getId() { return id; }
    public String getBaseCurrency() { return baseCurrency; }
    public String getQuoteCurrency() { return quoteCurrency; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getOpenRate() { return openRate; }
    public BigDecimal getHighRate() { return highRate; }
    public BigDecimal getLowRate() { return lowRate; }
    public BigDecimal getCloseRate() { return closeRate; }
    public String getRateType() { return rateType; }
    public String getSource() { return source; }
    public String getProviderSymbol() { return providerSymbol; }
    public Instant getAsOf() { return asOf; }
    public Instant getFetchedAt() { return fetchedAt; }
}
