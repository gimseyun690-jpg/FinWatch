package com.finwatch.news.content;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record ArticleHttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {

    public Optional<String> firstHeader(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).equals(normalizedName))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst();
    }
}
