package com.finwatch.account.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.finwatch.account.dto.AccountDeletionRequest;
import com.finwatch.auth.session.AuthenticatedSession;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.identity.AuthProvider;
import com.finwatch.user.repository.AppUserRepository;

@Service
public class AccountDeletionService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public AccountDeletionService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void delete(AuthenticatedSession session, AccountDeletionRequest request) {
        if (!"DELETE".equals(request.confirmation())) {
            throw new IllegalArgumentException("계정 삭제 확인 문구가 올바르지 않습니다.");
        }
        AppUser user = userRepository.findById(session.user().getId())
                .orElseThrow(() -> new BadCredentialsException("로그인 사용자를 찾을 수 없습니다."));
        verifyReauthentication(user, session.provider(), request.password());

        Long userId = user.getId();
        jdbcTemplate.update("DELETE FROM watchlists WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM portfolio_holdings WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM price_alerts WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE ai_usage_logs SET user_id = NULL WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM auth_identities WHERE user_id = ?", userId);
        user.markDeleted();
    }

    private void verifyReauthentication(AppUser user, AuthProvider provider, String password) {
        if (provider == AuthProvider.LOCAL) {
            if (password == null || user.getPasswordHash() == null
                    || !passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new BadCredentialsException("계정 삭제를 위해 현재 비밀번호가 필요합니다.");
            }
            return;
        }
        Instant lastLoginAt = user.getLastLoginAt();
        if (lastLoginAt == null || lastLoginAt.isBefore(Instant.now().minus(10, ChronoUnit.MINUTES))) {
            throw new BadCredentialsException("계정 삭제 전에 카카오 로그인을 다시 진행해 주세요.");
        }
    }
}
