package com.finwatch.stock.service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.dto.StockSearchResponses.StockSearchItem;
import com.finwatch.stock.dto.StockSearchResponses.StockSearchPage;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;

@Service
@Transactional(readOnly = true)
public class StockSearchService {

    private static final Pattern ALLOWED_QUERY = Pattern.compile("[\\p{L}\\p{N} .&'\\-]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ALLOWED_FILTER = Pattern.compile("[A-Z0-9._-]{2,30}");

    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;

    public StockSearchService(StockRepository stockRepository, MarketPriceRepository marketPriceRepository) {
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
    }

    public StockSearchPage search(String rawQuery, String rawMarket, String rawType, int page, int size) {
        String query = normalizeQuery(rawQuery);
        String market = normalizeFilter(rawMarket, "market");
        String instrumentType = normalizeFilter(rawType, "type");
        if (page < 0) {
            throw invalid("STOCK_SEARCH_PAGE_INVALID", "page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > 50) {
            throw invalid("STOCK_SEARCH_SIZE_INVALID", "size는 1~50 범위여야 합니다.");
        }

        Page<Stock> result = stockRepository.searchCatalog(
                query,
                market,
                instrumentType,
                PageRequest.of(page, size));
        List<Long> ids = result.getContent().stream().map(Stock::getId).toList();
        Set<Long> readyIds = ids.isEmpty()
                ? Set.of()
                : new HashSet<>(marketPriceRepository.findStockIdsWithPrices(ids));
        List<StockSearchItem> items = result.getContent().stream()
                .map(stock -> toItem(stock, readyIds.contains(stock.getId())))
                .toList();
        Instant catalogAsOf = stockRepository.findCatalogAsOf().orElse(Instant.EPOCH);
        return new StockSearchPage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                catalogAsOf);
    }

    private StockSearchItem toItem(Stock stock, boolean ready) {
        return new StockSearchItem(
                stock.getId(),
                stock.getMarket(),
                stock.getExchange(),
                stock.getSymbol(),
                stock.getName(),
                stock.getEnglishName(),
                stock.getInstrumentType(),
                stock.getCurrency(),
                stock.isActive(),
                stock.isTradable(),
                stock.getStatus(),
                ready ? "READY" : "METADATA_ONLY",
                stock.getProvider());
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw invalid("STOCK_SEARCH_QUERY_REQUIRED", "검색어를 입력해 주세요.");
        }
        String trimmed = Normalizer.normalize(rawQuery.trim(), Normalizer.Form.NFKC);
        if (trimmed.length() > 100) {
            throw invalid("STOCK_SEARCH_QUERY_TOO_LONG", "검색어는 100자 이하여야 합니다.");
        }
        if (!ALLOWED_QUERY.matcher(trimmed).matches()) {
            throw invalid("STOCK_SEARCH_QUERY_INVALID", "검색어에 허용되지 않은 문자가 포함되어 있습니다.");
        }
        return trimmed.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeFilter(String rawValue, String field) {
        if (rawValue == null || rawValue.isBlank()) return null;
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_FILTER.matcher(normalized).matches()) {
            throw invalid("STOCK_SEARCH_FILTER_INVALID", field + " 필터 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private StockQueryException invalid(String code, String message) {
        return new StockQueryException(HttpStatus.BAD_REQUEST, code, message);
    }
}
