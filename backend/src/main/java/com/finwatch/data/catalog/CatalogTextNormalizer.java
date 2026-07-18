package com.finwatch.data.catalog;

import java.text.Normalizer;
import java.util.Locale;

public final class CatalogTextNormalizer {

    private CatalogTextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s._-]+", "")
                .trim();
    }
}
