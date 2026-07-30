package com.finwatch.fx.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.finwatch.fx.domain.ExchangeRate;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyOrderByAsOfDesc(
            String baseCurrency, String quoteCurrency);

    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyAndSourceNotOrderByAsOfDesc(
            String baseCurrency, String quoteCurrency, String excludedSource);

    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyAndSourceAndAsOf(
            String baseCurrency, String quoteCurrency, String source, Instant asOf);

    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyAndSourceAndAsOfBeforeOrderByAsOfDesc(
            String baseCurrency, String quoteCurrency, String source, Instant before);

    Optional<ExchangeRate> findFirstByBaseCurrencyAndQuoteCurrencyAndAsOfBeforeOrderByAsOfDesc(
            String baseCurrency, String quoteCurrency, Instant before);

    List<ExchangeRate> findByBaseCurrencyAndQuoteCurrencyAndAsOfBetweenOrderByAsOfAsc(
            String baseCurrency, String quoteCurrency, Instant from, Instant to);

    List<ExchangeRate> findByBaseCurrencyAndQuoteCurrencyAndSourceNotAndAsOfBetweenOrderByAsOfAsc(
            String baseCurrency, String quoteCurrency, String excludedSource, Instant from, Instant to);

    boolean existsByBaseCurrencyAndQuoteCurrencyAndSourceAndAsOf(
            String baseCurrency, String quoteCurrency, String source, Instant asOf);
}
