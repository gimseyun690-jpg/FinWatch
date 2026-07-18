package com.finwatch.watchlist.domain;

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
        name = "watchlists",
        uniqueConstraints = @UniqueConstraint(name = "uk_watchlists_user_stock", columnNames = { "user_id", "stock_id" }))
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Watchlist() {
    }

    public static Watchlist create(AppUser user, Stock stock) {
        Watchlist watchlist = new Watchlist();
        watchlist.user = user;
        watchlist.stock = stock;
        watchlist.createdAt = Instant.now();
        return watchlist;
    }

    public Long getId() {
        return id;
    }

    public Stock getStock() {
        return stock;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
