package com.finwatch.data.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogSyncResponse;
import com.finwatch.data.catalog.InstrumentCatalogSyncService;
import com.finwatch.data.sync.DataSyncResponses.DataSyncResponse;
import com.finwatch.data.sync.ExternalDataSyncService;

@RestController
@RequestMapping("/api/v1/admin/data")
public class AdminDataSyncController {

    private final ExternalDataSyncService externalDataSyncService;
    private final InstrumentCatalogSyncService instrumentCatalogSyncService;

    public AdminDataSyncController(
            ExternalDataSyncService externalDataSyncService,
            InstrumentCatalogSyncService instrumentCatalogSyncService) {
        this.externalDataSyncService = externalDataSyncService;
        this.instrumentCatalogSyncService = instrumentCatalogSyncService;
    }

    @PostMapping("/sync")
    public ApiResponse<DataSyncResponse> syncAll() {
        return ApiResponse.success(externalDataSyncService.syncAll(), "외부 데이터 동기화가 완료되었습니다.");
    }

    @PostMapping("/stocks/{symbol}/sync")
    public ApiResponse<DataSyncResponse> syncStock(@PathVariable String symbol) {
        return ApiResponse.success(externalDataSyncService.syncStock(symbol), "종목 데이터 동기화가 완료되었습니다.");
    }

    @PostMapping("/catalogs/sync")
    public ApiResponse<CatalogSyncResponse> syncCatalogs() {
        return ApiResponse.success(instrumentCatalogSyncService.syncAll(), "종목 마스터 동기화를 처리했습니다.");
    }

    @PostMapping("/catalogs/{provider}/sync")
    public ApiResponse<CatalogSyncResponse> syncCatalog(@PathVariable String provider) {
        return ApiResponse.success(
                instrumentCatalogSyncService.syncProvider(provider),
                "종목 마스터 동기화를 처리했습니다.");
    }
}
