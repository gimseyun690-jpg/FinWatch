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
            BigDecimal averagePurchasePrice) {
        Instant now = Instant.now();
        PortfolioHolding holding = new PortfolioHolding();
        holding.user = user;
        holding.stock = stock;
        holding.quantity = quantity;
        holding.averagePurchasePrice = averagePurchasePrice;
        holding.currency = stock.getCurrency();
        holding.createdAt = now;
        holding.updatedAt = now;
        return holding;
    }

    public void update(BigDecimal quantity, BigDecimal averagePurchasePrice) {
        if (quantity != null) this.quantity = quantity;
        if (averagePurchasePrice != null) this.averagePurchasePrice = averagePurchasePrice;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getAveragePurchasePrice() { return averagePurchasePrice; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
