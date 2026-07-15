package com.finwatch.data.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogSnapshot;
import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogWriteResult;
import com.finwatch.data.catalog.InstrumentCatalogResponses.ProviderInstrument;
import com.finwatch.stock.repository.StockRepository;

class InstrumentCatalogSyncServiceTest {

    private InstrumentCatalogProvider provider;
    private InstrumentCatalogSyncRunRepository runRepository;
    private InstrumentCatalogWriter writer;
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        provider = mock(InstrumentCatalogProvider.class);
        runRepository = mock(InstrumentCatalogSyncRunRepository.class);
        writer = mock(InstrumentCatalogWriter.class);
        stockRepository = mock(StockRepository.class);
        when(provider.providerId()).thenReturn("TEST_PROVIDER");
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void demoModeNeverCallsCatalogProvider() {
        var result = service("DEMO", 1).syncAll();

        assertThat(result.providers()).singleElement().extracting("status").isEqualTo("SKIPPED");
        verify(provider, never()).fetchCatalog();
        verifyNoInteractions(writer, stockRepository);
    }

    @Test
    void liveModeWritesValidatedSnapshot() {
        CatalogSnapshot snapshot = new CatalogSnapshot(
                "TEST_PROVIDER", "TEST", Instant.now(), List.of(instrument("AAPL")));
        when(provider.fetchCatalog()).thenReturn(snapshot);
        when(writer.replaceSnapshot(snapshot)).thenReturn(new CatalogWriteResult(1, 1, 0, 0));

        var result = service("LIVE", 1).syncAll();

        assertThat(result.providers()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("SUCCEEDED");
            assertThat(item.inserted()).isEqualTo(1);
        });
        verify(writer).replaceSnapshot(snapshot);
    }

    @Test
    void emptySnapshotFailsWithoutTouchingExistingCatalog() {
        when(provider.fetchCatalog()).thenReturn(
                new CatalogSnapshot("TEST_PROVIDER", "TEST", Instant.now(), List.of()));

        var result = service("LIVE", 1).syncAll();

        assertThat(result.providers()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("FAILED");
            assertThat(item.received()).isZero();
        });
        verify(writer, never()).replaceSnapshot(any());
    }

    private InstrumentCatalogSyncService service(String mode, int minimumReceived) {
        return new InstrumentCatalogSyncService(
                mode,
                List.of(provider),
                runRepository,
                writer,
                stockRepository,
                minimumReceived,
                0.5);
    }

    private ProviderInstrument instrument(String symbol) {
        return new ProviderInstrument(
                "NASDAQ:" + symbol,
                "NASDAQ",
                "NASDAQ",
                symbol,
                "Apple Inc.",
                "Apple Inc.",
                "STOCK",
                "USD",
                null,
                null,
                true,
                true,
                "LISTED");
    }
}
