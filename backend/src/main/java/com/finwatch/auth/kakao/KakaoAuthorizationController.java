package com.finwatch.auth.kakao;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.finwatch.auth.session.SessionCookieWriter;
import com.finwatch.auth.session.SessionUnavailableException;
import com.finwatch.auth.session.WebSessionService;
import com.finwatch.common.api.ApiResponse;
import com.finwatch.user.identity.AuthProvider;
import com.finwatch.user.identity.SocialUserProvisioningService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/auth/kakao")
public class KakaoAuthorizationController {

    private final KakaoLoginProperties properties;
    private final OAuthAttemptService attemptService;
    private final KakaoCorrelationCookie correlationCookie;
    private final KakaoLoginRateLimiter rateLimiter;
    private final KakaoOidcClient oidcClient;
    private final SocialUserProvisioningService provisioningService;
    private final WebSessionService webSessionService;
    private final SessionCookieWriter sessionCookieWriter;

    public KakaoAuthorizationController(
            KakaoLoginProperties properties,
            OAuthAttemptService attemptService,
            KakaoCorrelationCookie correlationCookie,
            KakaoLoginRateLimiter rateLimiter,
            KakaoOidcClient oidcClient,
            SocialUserProvisioningService provisioningService,
            WebSessionService webSessionService,
            SessionCookieWriter sessionCookieWriter) {
        this.properties = properties;
        this.attemptService = attemptService;
        this.correlationCookie = correlationCookie;
        this.rateLimiter = rateLimiter;
        this.oidcClient = oidcClient;
        this.provisioningService = provisioningService;
        this.webSessionService = webSessionService;
        this.sessionCookieWriter = sessionCookieWriter;
    }

    @GetMapping("/status")
    public ApiResponse<KakaoStatusResponse> status() {
        return ApiResponse.success(
                new KakaoStatusResponse(properties.enabled()),
                "카카오 로그인 설정 조회 성공");
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam(required = false) String returnTo,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireEnabled();
        rateLimiter.check(request.getRemoteAddr());
        var attempt = attemptService.create(returnTo);
        correlationCookie.write(response, attempt.correlation());
        URI location = UriComponentsBuilder.fromUriString(properties.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", "openid")
                .queryParam("state", attempt.state())
                .queryParam("nonce", attempt.nonce())
                .queryParam("code_challenge", attempt.codeChallenge())
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireEnabled();
        String correlation = correlationCookie.read(request);
        try {
            var attempt = attemptService.consume(correlation, state);
            correlationCookie.clear(response);
            if (error != null) {
                return redirectLogin("kakao_cancelled");
            }
            if (code == null || code.isBlank()) {
                throw new KakaoLoginException(
                        HttpStatus.UNAUTHORIZED,
                        "KAKAO_CODE_MISSING",
                        "카카오 인가 코드가 없습니다.");
            }
            var profile = oidcClient.exchange(code, attempt.codeVerifier(), attempt.nonceHash());
            var user = provisioningService.provisionKakao(profile);
            var session = webSessionService.create(user, AuthProvider.KAKAO);
            sessionCookieWriter.write(response, session.rawSessionId(), session.expiresAt());
            return ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .location(frontendLocation(attempt.returnTo()))
                    .build();
        } catch (DisabledException exception) {
            correlationCookie.clear(response);
            return redirectLogin("account_disabled");
        } catch (SessionUnavailableException exception) {
            correlationCookie.clear(response);
            return redirectLogin("session_unavailable");
        } catch (KakaoLoginException exception) {
            correlationCookie.clear(response);
            return redirectLogin(errorCode(exception));
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new KakaoLoginException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "KAKAO_LOGIN_DISABLED",
                    "카카오 로그인이 아직 설정되지 않았습니다.");
        }
    }

    private ResponseEntity<Void> redirectLogin(String errorCode) {
        URI location = UriComponentsBuilder.fromUri(frontendLocation("/login"))
                .queryParam("error", errorCode)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }

    private URI frontendLocation(String route) {
        return URI.create(properties.frontendBaseUrl() + route);
    }

    private String errorCode(KakaoLoginException exception) {
        return switch (exception.getCode()) {
            case "KAKAO_ATTEMPT_INVALID" -> "kakao_request_invalid";
            case "KAKAO_TOKEN_UNAVAILABLE" -> "kakao_unavailable";
            default -> "kakao_failed";
        };
    }

    public record KakaoStatusResponse(boolean enabled) {
    }
}
