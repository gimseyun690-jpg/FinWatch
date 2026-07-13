package com.finwatch.alert.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.finwatch.alert.domain.AlertStatus;
import com.finwatch.alert.domain.PriceAlert;
import com.finwatch.alert.dto.AlertCreateRequest;
import com.finwatch.alert.dto.AlertResponse;
import com.finwatch.alert.dto.AlertUpdateRequest;
import com.finwatch.alert.repository.PriceAlertRepository;
import com.finwatch.stock.domain.MarketPrice;
import com.finwatch.stock.domain.Stock;
import com.finwatch.stock.repository.MarketPriceRepository;
import com.finwatch.stock.repository.StockRepository;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.repository.AppUserRepository;

@Service
public class AlertService {

    private final PriceAlertRepository alertRepository;
    private final AppUserRepository userRepository;
    private final StockRepository stockRepository;
    private final MarketPriceRepository marketPriceRepository;

    public AlertService(
            PriceAlertRepository alertRepository,
            AppUserRepository userRepository,
            StockRepository stockRepository,
            MarketPriceRepository marketPriceRepository) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.marketPriceRepository = marketPriceRepository;
    }

    @Transactional
    public List<AlertResponse> getAlerts(Long userId) {
        return alertRepository.findAllWithStockByUserId(userId).stream()
                .map(alert -> {
                    MarketPrice latest = latest(alert.getStock());
                    if (latest != null) alert.evaluate(latest.getClosePrice(), latest.getRecordedAt());
                    return toResponse(alert, latest);
                })
                .toList();
    }

    @Transactional
    public AlertResponse create(Long userId, AlertCreateRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        Stock stock = stockRepository.findFirstBySymbolAndActiveTrue(request.symbol().trim().toUpperCase(Locale.ROOT))
                .orElseThrow(AlertException::stockNotFound);
        if (!stock.getCurrency().equalsIgnoreCase(request.currency().trim())) throw AlertException.currencyMismatch();

        PriceAlert duplicate = alertRepository.findDuplicate(userId, stock.getId(), request.condition(), request.targetPrice())
                .orElse(null);
        if (duplicate != null) {
            if (duplicate.getStatus() == AlertStatus.DISABLED) {
                duplicate.reactivate();
                return toResponse(duplicate, latest(stock));
            }
            throw AlertException.duplicated();
        }

        PriceAlert alert = alertRepository.save(PriceAlert.create(user, stock, request.condition(), request.targetPrice()));
        return toResponse(alert, latest(stock));
    }

    @Transactional
    public AlertResponse update(Long userId, Long alertId, AlertUpdateRequest request) {
        PriceAlert alert = alertRepository.findWithStockByIdAndUserId(alertId, userId)
                .orElseThrow(AlertException::notFound);
        alert.update(request.condition(), request.targetPrice(), request.status());
        MarketPrice latest = latest(alert.getStock());
        if (latest != null && alert.getStatus() == AlertStatus.ACTIVE) {
            alert.evaluate(latest.getClosePrice(), latest.getRecordedAt());
        }
        return toResponse(alert, latest);
    }

    @Transactional
    public void delete(Long userId, Long alertId) {
        PriceAlert alert = alertRepository.findWithStockByIdAndUserId(alertId, userId)
                .orElseThrow(AlertException::notFound);
        alertRepository.delete(alert);
    }

    private MarketPrice latest(Stock stock) {
        return marketPriceRepository.findTopByStockIdOrderByRecordedAtDesc(stock.getId()).orElse(null);
    }

    private AlertResponse toResponse(PriceAlert alert, MarketPrice latest) {
        String evaluationStatus = latest == null ? "PRICE_UNAVAILABLE"
                : alert.getStatus() == AlertStatus.DISABLED ? "NOT_EVALUATED"
                : alert.getStatus() == AlertStatus.TRIGGERED ? "CONDITION_MET" : "WAITING";
        return new AlertResponse(
                alert.getId(), alert.getStock().getSymbol(), alert.getStock().getName(), alert.getStock().getMarket(),
                alert.getCondition(), alert.getTargetPrice(), alert.getCurrency(), alert.getStatus(),
                latest == null ? null : latest.getClosePrice(), latest == null ? null : latest.getRecordedAt(),
                evaluationStatus, alert.getTriggeredAt(), alert.getCreatedAt());
    }
}
