package com.finwatch.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WatchlistCreateRequest(
        @Size(max = 30) String market,
        @NotBlank @Size(max = 30) String symbol) {
}
