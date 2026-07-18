package com.finwatch.portfolio.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.finwatch.stock.domain.Stock;
import com.finwatch.user.domain.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "portfolio_holdings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_portfolio_holdings_user_stock",
                columnNames = { "user_id", "stock_id" }))
public class PortfolioHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal quantity;

    @Column(name = "average_purchase_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal averagePurchasePrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "average_purchase_fx_rate", precision = 24, scale = 10)
    private BigDecimal averagePurchaseFxRate;

    @Column(name = "purchase_fx_base_currency", length = 3)
    private String purchaseFxBaseCurrency;

    @Column(name = "purchase_fx_quote_currency", length = 3)
    private String purchaseFxQuoteCurrency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PortfolioHolding() {
    }

    public static PortfolioHolding create(
            AppUser user,
            Stock stock,
            BigDecimal quantity,
            BigDecimal averagePurchasePrice,
            BigDecimal averagePurchaseFxRate,
            String purchaseFxBaseCurrency,
            String purchaseFxQuoteCurrency) {
        Instant now = Instant.now();
        PortfolioHolding holding = new PortfolioHolding();
        holding.user = user;
        holding.stock = stock;
        holding.quantity = quantity;
        holding.averagePurchasePrice = averagePurchasePrice;
        holding.currency = stock.getCurrency();
        holding.averagePurchaseFxRate = averagePurchaseFxRate;
        holding.purchaseFxBaseCurrency = purchaseFxBaseCurrency;
        holding.purchaseFxQuoteCurrency = purchaseFxQuoteCurrency;
        holding.createdAt = now;
        holding.updatedAt = now;
        return holding;
    }

    public void update(
            BigDecimal quantity,
            BigDecimal averagePurchasePrice,
            BigDecimal averagePurchaseFxRate,
            String purchaseFxBaseCurrency,
            String purchaseFxQuoteCurrency,
            boolean fxFieldsPresent) {
        if (quantity != null) this.quantity = quantity;
        if (averagePurchasePrice != null) this.averagePurchasePrice = averagePurchasePrice;
        if (fxFieldsPresent) {
            this.averagePurchaseFxRate = averagePurchaseFxRate;
            this.purchaseFxBaseCurrency = purchaseFxBaseCurrency;
            this.purchaseFxQuoteCurrency = purchaseFxQuoteCurrency;
        }
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAveragePurchasePrice() { return averagePurchasePrice; }
    public String getCurrency() { return currency; }
    public BigDecimal getAveragePurchaseFxRate() { return averagePurchaseFxRate; }
    public String getPurchaseFxBaseCurrency() { return purchaseFxBaseCurrency; }
    public String getPurchaseFxQuoteCurrency() { return purchaseFxQuoteCurrency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
