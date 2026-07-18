package com.finwatch.user.identity;

import java.time.Instant;

import com.finwatch.user.domain.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "auth_identities", uniqueConstraints = @UniqueConstraint(
        name = "uk_auth_identities_provider_subject",
        columnNames = {"provider", "provider_subject"}))
public class AuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AuthIdentity() {
    }

    public static AuthIdentity kakao(AppUser user, String subject, String providerEmail) {
        Instant now = Instant.now();
        AuthIdentity identity = new AuthIdentity();
        identity.user = user;
        identity.provider = AuthProvider.KAKAO;
        identity.providerSubject = subject;
        identity.providerEmail = normalizeEmail(providerEmail);
        identity.createdAt = now;
        identity.updatedAt = now;
        identity.lastLoginAt = now;
        return identity;
    }

    public void recordLogin(String providerEmail) {
        this.providerEmail = normalizeEmail(providerEmail);
        this.lastLoginAt = Instant.now();
        this.updatedAt = this.lastLoginAt;
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    public AppUser getUser() {
        return user;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }
}
