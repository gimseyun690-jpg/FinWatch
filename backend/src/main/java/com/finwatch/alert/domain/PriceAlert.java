package com.finwatch.alert.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.finwatch.stock.domain.Stock;
import com.finwatch.user.domain.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_alerts")
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_condition", nullable = false, length = 10)
    private AlertCondition condition;

    @Column(name = "target_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal targetPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AlertStatus status;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "last_evaluated_price", precision = 20, scale = 4)
    private BigDecimal lastEvaluatedPrice;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PriceAlert() {
    }

    public static PriceAlert create(
            AppUser user,
            Stock stock,
            AlertCondition condition,
            BigDecimal targetPrice) {
        Instant now = Instant.now();
        PriceAlert alert = new PriceAlert();
        alert.user = user;
        alert.stock = stock;
        alert.condition = condition;
        alert.targetPrice = targetPrice;
        alert.currency = stock.getCurrency();
        alert.status = AlertStatus.ACTIVE;
        alert.createdAt = now;
        alert.updatedAt = now;
        return alert;
    }

    public void evaluate(BigDecimal price, Instant priceAsOf) {
        if (status != AlertStatus.ACTIVE) return;
        lastEvaluatedPrice = price;
        lastEvaluatedAt = priceAsOf;
        boolean matched = condition == AlertCondition.ABOVE
                ? price.compareTo(targetPrice) >= 0
                : price.compareTo(targetPrice) <= 0;
        if (matched) {
            status = AlertStatus.TRIGGERED;
            triggeredAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    public void update(AlertCondition newCondition, BigDecimal newTargetPrice, AlertStatus newStatus) {
        boolean ruleChanged = newCondition != null || newTargetPrice != null;
        if (newCondition != null) condition = newCondition;
        if (newTargetPrice != null) targetPrice = newTargetPrice;
        if (ruleChanged) reactivate();
        if (newStatus == AlertStatus.ACTIVE) reactivate();
        if (newStatus == AlertStatus.DISABLED) status = AlertStatus.DISABLED;
        updatedAt = Instant.now();
    }

    public void reactivate() {
        status = AlertStatus.ACTIVE;
        triggeredAt = null;
        lastEvaluatedPrice = null;
        lastEvaluatedAt = null;
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Stock getStock() { return stock; }
    public AlertCondition getCondition() { return condition; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public String getCurrency() { return currency; }
    public AlertStatus getStatus() { return status; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public BigDecimal getLastEvaluatedPrice() { return lastEvaluatedPrice; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
