package com.finwatch.data.catalog;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "instrument_catalog_sync_runs")
public class InstrumentCatalogSyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "received_count", nullable = false)
    private int receivedCount;

    @Column(name = "inserted_count", nullable = false)
    private int insertedCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "deactivated_count", nullable = false)
    private int deactivatedCount;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected InstrumentCatalogSyncRun() {
    }

    public static InstrumentCatalogSyncRun start(String provider, Instant startedAt) {
        InstrumentCatalogSyncRun run = new InstrumentCatalogSyncRun();
        run.provider = provider;
        run.startedAt = startedAt;
        run.status = "RUNNING";
        return run;
    }

    public void succeed(InstrumentCatalogResponses.CatalogWriteResult result, Instant finishedAt) {
        this.finishedAt = finishedAt;
        this.status = "SUCCEEDED";
        this.receivedCount = result.received();
        this.insertedCount = result.inserted();
        this.updatedCount = result.updated();
        this.deactivatedCount = result.deactivated();
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void fail(String code, String message, int receivedCount, Instant finishedAt) {
        this.finishedAt = finishedAt;
        this.status = "FAILED";
        this.receivedCount = receivedCount;
        this.errorCode = truncate(code, 80);
        this.errorMessage = truncate(message, 500);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
