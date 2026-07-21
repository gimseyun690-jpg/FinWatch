package com.finwatch.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.annotation.Order;

import com.finwatch.user.domain.AppUser;
import com.finwatch.user.domain.UserRole;
import com.finwatch.user.repository.AppUserRepository;

@Component
@Order(1)
public class DemoUserInitializer implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String userPassword;
    private final String adminPassword;

    public DemoUserInitializer(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.demo-user-password}") String userPassword,
            @Value("${app.auth.demo-admin-password}") String adminPassword) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.userPassword = userPassword;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing("user@finwatch.local", userPassword, UserRole.USER);
        createIfMissing("admin@finwatch.local", adminPassword, UserRole.ADMIN);
    }

    private void createIfMissing(String email, String password, UserRole role) {
        var userOpt = appUserRepository.findByEmailIgnoreCase(email);
        if (userOpt.isEmpty()) {
            appUserRepository.save(AppUser.create(email, passwordEncoder.encode(password), role));
        } else {
            var user = userOpt.get();
            user.updatePassword(passwordEncoder.encode(password));
            appUserRepository.save(user);
        }
    }
}
