package com.finwatch.alert.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.alert.domain.AlertCondition;
import com.finwatch.alert.domain.AlertStatus;
import com.finwatch.alert.repository.PriceAlertRepository;
import com.finwatch.realtime.LiveQuote;

@Service
public class RealtimeAlertEvaluationService {

    private final PriceAlertRepository alertRepository;

    public RealtimeAlertEvaluationService(PriceAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Transactional
    public int evaluate(LiveQuote quote) {
        var matches = alertRepository.findMatchingActiveAlerts(
                quote.symbol(),
                quote.price(),
                AlertStatus.ACTIVE,
                AlertCondition.ABOVE,
                AlertCondition.BELOW);
        matches.forEach(alert -> alert.evaluate(quote.price(), quote.asOf()));
        return matches.size();
    }
}
