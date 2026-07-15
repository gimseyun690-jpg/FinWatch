package com.finwatch.data.catalog;

import com.finwatch.data.catalog.InstrumentCatalogResponses.CatalogSnapshot;

public interface InstrumentCatalogProvider {

    String providerId();

    CatalogSnapshot fetchCatalog();
}
