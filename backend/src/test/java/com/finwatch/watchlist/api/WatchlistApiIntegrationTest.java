package com.finwatch.watchlist.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.finwatch.user.domain.AppUser;
import com.finwatch.user.repository.AppUserRepository;
import com.finwatch.watchlist.repository.WatchlistRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class WatchlistApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    private Long userId;
    private Long adminId;

    @BeforeEach
    void resetWatchlists() {
        watchlistRepository.deleteAll();
        userId = user("user@finwatch.local").getId();
        adminId = user("admin@finwatch.local").getId();
    }

    @Test
    void crudIsIsolatedByAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/v1/watchlists")
                        .with(jwtFor(userId, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"000660\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.symbol").value("000660"))
                .andExpect(jsonPath("$.data.name").value("SK하이닉스"));

        mockMvc.perform(post("/api/v1/watchlists")
                        .with(jwtFor(userId, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"000660\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WATCHLIST_DUPLICATED"));

        mockMvc.perform(get("/api/v1/watchlists").with(jwtFor(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].symbol").value("000660"));

        mockMvc.perform(get("/api/v1/watchlists").with(jwtFor(adminId, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(delete("/api/v1/watchlists/000660").with(jwtFor(adminId, "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WATCHLIST_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/watchlists/000660").with(jwtFor(userId, "USER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/watchlists").with(jwtFor(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void canonicalSearchResultsCanGrowBeyondFourAndKeepMetadataOnlyStocks() throws Exception {
        String[][] stocks = {
                {"KRX", "005930"},
                {"KRX", "035420"},
                {"NASDAQ", "AAPL"},
                {"NASDAQ", "MSFT"},
                {"NASDAQ", "TSLA"}
        };
        for (String[] stock : stocks) {
            mockMvc.perform(post("/api/v1/watchlists")
                            .with(jwtFor(userId, "USER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"market\":\"" + stock[0] + "\",\"symbol\":\"" + stock[1] + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.market").value(stock[0]))
                    .andExpect(jsonPath("$.data.symbol").value(stock[1]));
        }

        mockMvc.perform(get("/api/v1/watchlists").with(jwtFor(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[4].symbol").value("TSLA"))
                .andExpect(jsonPath("$.data[4].price").doesNotExist())
                .andExpect(jsonPath("$.data[4].dataAvailability").value("METADATA_ONLY"));

        mockMvc.perform(delete("/api/v1/watchlists/NASDAQ/TSLA").with(jwtFor(userId, "USER")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/watchlists").with(jwtFor(userId, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    private AppUser user(String email) {
        return appUserRepository.findByEmailIgnoreCase(email).orElseThrow();
    }

    private RequestPostProcessor jwtFor(Long id, String role) {
        return jwt()
                .jwt(builder -> builder.claim("userId", id))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
