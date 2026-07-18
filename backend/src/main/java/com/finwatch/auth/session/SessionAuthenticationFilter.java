package com.finwatch.auth.session;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = SessionAuthenticationFilter.class.getName() + ".session";

    private final WebSessionService webSessionService;
    private final SessionCookieWriter cookieWriter;

    public SessionAuthenticationFilter(
            WebSessionService webSessionService,
            SessionCookieWriter cookieWriter) {
        this.webSessionService = webSessionService;
        this.cookieWriter = cookieWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawSessionId = cookieWriter.read(request);
        if (rawSessionId != null) {
            try {
                var session = webSessionService.authenticate(rawSessionId);
                if (session.isPresent()) {
                    authenticate(request, session.get());
                } else {
                    cookieWriter.clear(response);
                }
            } catch (SessionUnavailableException exception) {
                cookieWriter.clear(response);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, AuthenticatedSession session) {
        var user = session.user();
        String subject = user.getEmail() == null ? "user:" + user.getId() : user.getEmail();
        Jwt jwt = Jwt.withTokenValue(session.sessionHash())
                .header("alg", "SESSION")
                .subject(subject)
                .issuedAt(session.createdAt())
                .expiresAt(session.expiresAt())
                .claim("userId", user.getId())
                .claim("roles", List.of(user.getRole().name()))
                .claim("authProvider", session.provider().name())
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities, subject));
        request.setAttribute(SESSION_ATTRIBUTE, session);
    }
}
