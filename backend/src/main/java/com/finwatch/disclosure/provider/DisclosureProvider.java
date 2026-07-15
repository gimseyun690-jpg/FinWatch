package com.finwatch.disclosure.provider;

import java.time.LocalDate;

import com.finwatch.disclosure.provider.DisclosureProviderResponses.DisclosureFetchResult;
import com.finwatch.stock.domain.Stock;

public interface DisclosureProvider {

    String providerId();

    boolean supports(Stock stock);

    DisclosureFetchResult fetch(Stock stock, LocalDate from, LocalDate to);
}
