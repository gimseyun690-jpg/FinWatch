package com.finwatch.auth.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finwatch.auth.dto.AuthUserResponse;
import com.finwatch.auth.dto.LoginRequest;
import com.finwatch.auth.dto.LoginResponse;
import com.finwatch.auth.service.JwtTokenService.IssuedToken;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.repository.AppUserRepository;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        IssuedToken token = jwtTokenService.issue(user);
        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresInSeconds(),
                AuthUserResponse.from(user));
    }
}
