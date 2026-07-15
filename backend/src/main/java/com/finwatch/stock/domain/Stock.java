package com.finwatch.stock.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.finwatch.data.catalog.CatalogTextNormalizer;
import com.finwatch.data.catalog.InstrumentCatalogResponses.ProviderInstrument;

@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 30)
    private String market;

    @Column(nullable = false, length = 30)
    private String exchange;

    @Column(name = "english_name", length = 150)
    private String englishName;

    @Column(name = "normalized_name", nullable = false, length = 150)
    private String normalizedName;

    @Column(name = "normalized_english_name", length = 150)
    private String normalizedEnglishName;

    @Column(name = "instrument_type", nullable = false, length = 30)
    private String instrumentType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean tradable;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_instrument_id", nullable = false, length = 100)
    private String providerInstrumentId;

    @Column(length = 20)
    private String isin;

    @Column(name = "listed_at")
    private LocalDate listedAt;

    @Column(name = "delisted_at")
    private LocalDate delistedAt;

    @Column(name = "catalog_updated_at", nullable = false)
    private Instant catalogUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Stock() {
    }

    public static Stock fromCatalog(String provider, ProviderInstrument instrument, Instant now) {
        Stock stock = new Stock();
        stock.createdAt = now;
        stock.applyCatalog(provider, instrument, now);
        return stock;
    }

    public void applyCatalog(String provider, ProviderInstrument instrument, Instant now) {
        this.symbol = instrument.symbol();
        this.name = instrument.name();
        this.market = instrument.market();
        this.exchange = instrument.exchange();
        this.englishName = blankToNull(instrument.englishName());
        this.normalizedName = CatalogTextNormalizer.normalize(instrument.name());
        this.normalizedEnglishName = blankToNull(CatalogTextNormalizer.normalize(instrument.englishName()));
        this.instrumentType = instrument.instrumentType();
        this.currency = instrument.currency();
        this.active = instrument.active();
        this.tradable = instrument.tradable();
        this.status = instrument.status();
        this.provider = provider;
        this.providerInstrumentId = instrument.providerInstrumentId();
        this.isin = blankToNull(instrument.isin());
        this.listedAt = instrument.listedAt();
        this.delistedAt = instrument.active() ? null : this.delistedAt;
        this.catalogUpdatedAt = now;
        this.updatedAt = now;
    }

    public void deactivateFromCatalog(Instant now) {
        this.active = false;
        this.tradable = false;
        this.status = "INACTIVE";
        this.delistedAt = LocalDate.now(java.time.ZoneOffset.UTC);
        this.catalogUpdatedAt = now;
        this.updatedAt = now;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getMarket() {
        return market;
    }

    public String getExchange() {
        return exchange;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getNormalizedEnglishName() {
        return normalizedEnglishName;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isTradable() {
        return tradable;
    }

    public String getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderInstrumentId() {
        return providerInstrumentId;
    }

    public String getIsin() {
        return isin;
    }

    public LocalDate getListedAt() {
        return listedAt;
    }

    public LocalDate getDelistedAt() {
        return delistedAt;
    }

    public Instant getCatalogUpdatedAt() {
        return catalogUpdatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
