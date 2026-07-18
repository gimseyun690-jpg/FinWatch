package com.finwatch.auth.api;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finwatch.auth.dto.LoginRequest;
import com.finwatch.auth.dto.LoginResponse;
import com.finwatch.auth.dto.AuthUserResponse;
import com.finwatch.auth.session.AuthenticatedSession;
import com.finwatch.auth.session.SessionAuthenticationFilter;
import com.finwatch.auth.session.SessionCookieWriter;
import com.finwatch.auth.session.WebSessionService;
import com.finwatch.auth.service.AuthService;
import com.finwatch.common.api.ApiResponse;
import com.finwatch.user.identity.AuthProvider;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final WebSessionService webSessionService;
    private final SessionCookieWriter cookieWriter;

    public AuthController(
            AuthService authService,
            WebSessionService webSessionService,
            SessionCookieWriter cookieWriter) {
        this.authService = authService;
        this.webSessionService = webSessionService;
        this.cookieWriter = cookieWriter;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response,
            CsrfToken csrfToken) {
        var user = authService.login(request);
        var session = webSessionService.create(user, AuthProvider.LOCAL);
        cookieWriter.write(response, session.rawSessionId(), session.expiresAt());
        csrfToken.getToken();
        return ApiResponse.success(
                new LoginResponse(true, session.expiresAt(), AuthUserResponse.from(user, AuthProvider.LOCAL)),
                "로그인 성공");
    }

    @GetMapping("/session")
    public ApiResponse<LoginResponse> session(HttpServletRequest request, CsrfToken csrfToken) {
        AuthenticatedSession session = (AuthenticatedSession) request.getAttribute(
                SessionAuthenticationFilter.SESSION_ATTRIBUTE);
        if (session == null) {
            throw new BadCredentialsException("인증 세션이 없습니다.");
        }
        csrfToken.getToken();
        return ApiResponse.success(
                new LoginResponse(
                        true,
                        session.expiresAt(),
                        AuthUserResponse.from(session.user(), session.provider())),
                "세션 조회 성공");
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        webSessionService.invalidate(cookieWriter.read(request));
        cookieWriter.clear(response);
        return ApiResponse.success(null, "로그아웃 성공");
    }
}
