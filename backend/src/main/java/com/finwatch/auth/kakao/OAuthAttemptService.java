package com.finwatch.auth.kakao;

import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.finwatch.auth.session.AuthHashing;
import com.finwatch.auth.session.SessionUnavailableException;

import tools.jackson.databind.ObjectMapper;

@Service
public class OAuthAttemptService {

    private static final String KEY_PREFIX = "auth:oauth:kakao:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KakaoLoginProperties properties;
    private final KakaoReturnToValidator returnToValidator;
    private final OAuthSecretProtector protector;

    public OAuthAttemptService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            KakaoLoginProperties properties,
            KakaoReturnToValidator returnToValidator,
            OAuthSecretProtector protector) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.returnToValidator = returnToValidator;
        this.protector = protector;
    }

    public CreatedAttempt create(String requestedReturnTo) {
        String correlation = AuthHashing.randomUrlToken(32);
        String state = AuthHashing.randomUrlToken(32);
        String nonce = AuthHashing.randomUrlToken(32);
        String verifier = AuthHashing.randomUrlToken(48);
        StoredAttempt stored = new StoredAttempt(
                AuthHashing.sha256(state),
                AuthHashing.sha256(nonce),
                protector.protect(verifier),
                returnToValidator.validate(requestedReturnTo),
                Instant.now());
        try {
            redisTemplate.opsForValue().set(
                    key(correlation),
                    objectMapper.writeValueAsString(stored),
                    properties.attemptTtl());
        } catch (Exception exception) {
            throw new SessionUnavailableException("OAuth 요청 저장소를 사용할 수 없습니다.", exception);
        }
        return new CreatedAttempt(
                correlation,
                state,
                nonce,
                pkceChallenge(verifier),
                stored.returnTo());
    }

    public ConsumedAttempt consume(String correlation, String state) {
        if (correlation == null || correlation.isBlank() || state == null || state.isBlank()) {
            throw invalidAttempt();
        }
        String json;
        try {
            json = redisTemplate.opsForValue().getAndDelete(key(correlation));
        } catch (Exception exception) {
            throw new SessionUnavailableException("OAuth 요청 저장소를 사용할 수 없습니다.", exception);
        }
        if (json == null) {
            throw invalidAttempt();
        }
        try {
            StoredAttempt stored = objectMapper.readValue(json, StoredAttempt.class);
            if (!AuthHashing.secureEquals(stored.stateHash(), AuthHashing.sha256(state))) {
                throw invalidAttempt();
            }
            return new ConsumedAttempt(
                    stored.nonceHash(),
                    protector.unprotect(stored.protectedCodeVerifier()),
                    stored.returnTo());
        } catch (KakaoLoginException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new KakaoLoginException(
                    HttpStatus.UNAUTHORIZED,
                    "KAKAO_ATTEMPT_INVALID",
                    "카카오 로그인 요청을 확인할 수 없습니다.",
                    exception);
        }
    }

    static String pkceChallenge(String verifier) {
        return AuthHashing.sha256(verifier);
    }

    private String key(String correlation) {
        return KEY_PREFIX + AuthHashing.sha256(correlation);
    }

    private KakaoLoginException invalidAttempt() {
        return new KakaoLoginException(
                HttpStatus.UNAUTHORIZED,
                "KAKAO_ATTEMPT_INVALID",
                "카카오 로그인 요청이 만료되었거나 이미 사용되었습니다.");
    }

    public record CreatedAttempt(
            String correlation,
            String state,
            String nonce,
            String codeChallenge,
            String returnTo) {
    }

    public record ConsumedAttempt(String nonceHash, String codeVerifier, String returnTo) {
    }

    private record StoredAttempt(
            String stateHash,
            String nonceHash,
            String protectedCodeVerifier,
            String returnTo,
            Instant createdAt) {
    }
}
