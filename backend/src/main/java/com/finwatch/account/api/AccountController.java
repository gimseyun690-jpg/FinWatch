package com.finwatch.account.api;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.authentication.BadCredentialsException;

import com.finwatch.account.dto.AccountDeletionRequest;
import com.finwatch.account.service.AccountDeletionService;
import com.finwatch.auth.session.AuthenticatedSession;
import com.finwatch.auth.session.SessionAuthenticationFilter;
import com.finwatch.auth.session.SessionCookieWriter;
import com.finwatch.auth.session.WebSessionService;
import com.finwatch.common.api.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountDeletionService deletionService;
    private final WebSessionService sessionService;
    private final SessionCookieWriter cookieWriter;

    public AccountController(
            AccountDeletionService deletionService,
            WebSessionService sessionService,
            SessionCookieWriter cookieWriter) {
        this.deletionService = deletionService;
        this.sessionService = sessionService;
        this.cookieWriter = cookieWriter;
    }

    @DeleteMapping
    public ApiResponse<Void> delete(
            @Valid @RequestBody AccountDeletionRequest deletionRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthenticatedSession session = (AuthenticatedSession) request.getAttribute(
                SessionAuthenticationFilter.SESSION_ATTRIBUTE);
        if (session == null) {
            throw new BadCredentialsException("쿠키 세션 재인증이 필요합니다.");
        }
        deletionService.delete(session, deletionRequest);
        sessionService.invalidateAll(session.user().getId());
        cookieWriter.clear(response);
        return ApiResponse.success(null, "계정 삭제 완료");
    }
}
