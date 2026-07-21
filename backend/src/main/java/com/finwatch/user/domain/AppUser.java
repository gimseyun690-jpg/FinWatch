package com.finwatch.user.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public static AppUser create(String email, String passwordHash, UserRole role) {
        Instant now = Instant.now();
        AppUser user = new AppUser();
        user.email = email.trim().toLowerCase();
        user.passwordHash = passwordHash;
        user.displayName = defaultDisplayName(email);
        user.role = role;
        user.status = UserStatus.ACTIVE;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public static AppUser createSocial(String displayName, String profileImageUrl) {
        Instant now = Instant.now();
        AppUser user = new AppUser();
        user.displayName = normalizeDisplayName(displayName);
        user.profileImageUrl = normalizeNullable(profileImageUrl);
        user.role = UserRole.USER;
        user.status = UserStatus.ACTIVE;
        user.createdAt = now;
        user.updatedAt = now;
        user.lastLoginAt = now;
        return user;
    }

    public void recordLogin(String displayName, String profileImageUrl) {
        this.displayName = normalizeDisplayName(displayName == null ? this.displayName : displayName);
        if (profileImageUrl != null) {
            this.profileImageUrl = normalizeNullable(profileImageUrl);
        }
        this.lastLoginAt = Instant.now();
        this.updatedAt = this.lastLoginAt;
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public void markDeleted() {
        this.email = null;
        this.passwordHash = null;
        this.displayName = "탈퇴한 사용자";
        this.profileImageUrl = null;
        this.status = UserStatus.DELETED;
        this.lastLoginAt = null;
        this.updatedAt = Instant.now();
    }

    private static String defaultDisplayName(String email) {
        int separator = email.indexOf('@');
        return normalizeDisplayName(separator > 0 ? email.substring(0, separator) : email);
    }

    private static String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return "FinWatch 사용자";
        }
        String normalized = value.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
