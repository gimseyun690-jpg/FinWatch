package com.finwatch.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.finwatch.user.domain.AppUser;
import com.finwatch.user.domain.UserStatus;
import com.finwatch.user.identity.AuthProvider;
import com.finwatch.user.repository.AppUserRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class WebSessionService {

    private static final String SESSION_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_PREFIX = "auth:user-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppUserRepository appUserRepository;
    private final Duration idleTtl;
    private final Duration absoluteTtl;

    public WebSessionService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AppUserRepository appUserRepository,
            @Value("${app.auth.session.idle-ttl}") Duration idleTtl,
            @Value("${app.auth.session.absolute-ttl}") Duration absoluteTtl) {
        if (idleTtl.isNegative() || idleTtl.isZero() || absoluteTtl.compareTo(idleTtl) < 0) {
            throw new IllegalStateException("SESSION TTL values are invalid.");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.appUserRepository = appUserRepository;
        this.idleTtl = idleTtl;
        this.absoluteTtl = absoluteTtl;
    }

    public CreatedSession create(AppUser user, AuthProvider provider) {
        String rawSessionId = AuthHashing.randomUrlToken(32);
        String sessionHash = AuthHashing.sha256(rawSessionId);
        Instant now = Instant.now();
        Instant absoluteExpiresAt = now.plus(absoluteTtl);
        StoredSession stored = new StoredSession(
                user.getId(),
                user.getRole().name(),
                provider.name(),
                now,
                now,
                absoluteExpiresAt);
        write(sessionHash, stored, idleTtl);
        String userSessionsKey = USER_SESSIONS_PREFIX + user.getId();
        redisTemplate.opsForSet().add(userSessionsKey, sessionHash);
        redisTemplate.expire(userSessionsKey, absoluteTtl);
        return new CreatedSession(rawSessionId, absoluteExpiresAt);
    }

    public Optional<AuthenticatedSession> authenticate(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) {
            return Optional.empty();
        }
        String sessionHash = AuthHashing.sha256(rawSessionId);
        String json = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionHash);
        if (json == null) {
            return Optional.empty();
        }
        StoredSession stored = read(json);
        Instant now = Instant.now();
        if (!stored.absoluteExpiresAt().isAfter(now)) {
            invalidateByHash(sessionHash, stored.userId());
            return Optional.empty();
        }
        AppUser user = appUserRepository.findById(stored.userId()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            invalidateByHash(sessionHash, stored.userId());
            return Optional.empty();
        }
        Duration remainingAbsolute = Duration.between(now, stored.absoluteExpiresAt());
        Duration nextTtl = remainingAbsolute.compareTo(idleTtl) < 0 ? remainingAbsolute : idleTtl;
        StoredSession touched = new StoredSession(
                stored.userId(),
                user.getRole().name(),
                stored.provider(),
                stored.createdAt(),
                now,
                stored.absoluteExpiresAt());
        write(sessionHash, touched, nextTtl);
        return Optional.of(new AuthenticatedSession(
                sessionHash,
                user,
                AuthProvider.valueOf(stored.provider()),
                stored.createdAt(),
                stored.absoluteExpiresAt()));
    }

    public void invalidate(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) {
            return;
        }
        String sessionHash = AuthHashing.sha256(rawSessionId);
        String json = redisTemplate.opsForValue().getAndDelete(SESSION_PREFIX + sessionHash);
        if (json != null) {
            StoredSession stored = read(json);
            redisTemplate.opsForSet().remove(USER_SESSIONS_PREFIX + stored.userId(), sessionHash);
        }
    }

    public void invalidateAll(Long userId) {
        String key = USER_SESSIONS_PREFIX + userId;
        var sessionHashes = redisTemplate.opsForSet().members(key);
        if (sessionHashes != null && !sessionHashes.isEmpty()) {
            redisTemplate.delete(sessionHashes.stream().map(hash -> SESSION_PREFIX + hash).toList());
        }
        redisTemplate.delete(key);
    }

    private void invalidateByHash(String sessionHash, Long userId) {
        redisTemplate.delete(SESSION_PREFIX + sessionHash);
        redisTemplate.opsForSet().remove(USER_SESSIONS_PREFIX + userId, sessionHash);
    }

    private void write(String sessionHash, StoredSession stored, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    SESSION_PREFIX + sessionHash,
                    objectMapper.writeValueAsString(stored),
                    ttl);
        } catch (Exception exception) {
            throw new SessionUnavailableException("세션 저장소를 사용할 수 없습니다.", exception);
        }
    }

    private StoredSession read(String json) {
        try {
            return objectMapper.readValue(json, StoredSession.class);
        } catch (Exception exception) {
            throw new SessionUnavailableException("세션 데이터를 확인할 수 없습니다.", exception);
        }
    }

    public record CreatedSession(String rawSessionId, Instant expiresAt) {
    }

    private record StoredSession(
            Long userId,
            String role,
            String provider,
            Instant createdAt,
            Instant lastSeenAt,
            Instant absoluteExpiresAt) {
    }
}
