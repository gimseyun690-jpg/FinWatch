package com.finwatch.watchlist.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.common.api.ApiResponse;
import com.finwatch.watchlist.dto.WatchlistCreateRequest;
import com.finwatch.watchlist.dto.WatchlistItemResponse;
import com.finwatch.watchlist.service.WatchlistService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public ApiResponse<List<WatchlistItemResponse>> watchlist(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(watchlistService.getWatchlist(userId(jwt)), "관심종목 조회 성공");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WatchlistItemResponse> add(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WatchlistCreateRequest request) {
        return ApiResponse.success(
                watchlistService.add(userId(jwt), request.market(), request.symbol()),
                "관심종목 등록 성공");
    }

    @DeleteMapping("/{market}/{symbol}")
    public ApiResponse<Void> removeCanonical(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String market,
            @PathVariable String symbol) {
        watchlistService.remove(userId(jwt), market, symbol);
        return ApiResponse.success(null, "관심종목 삭제 성공");
    }

    @DeleteMapping("/{symbol}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String symbol) {
        watchlistService.remove(userId(jwt), symbol);
        return ApiResponse.success(null, "관심종목 삭제 성공");
    }

    private Long userId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(claim));
    }
}
