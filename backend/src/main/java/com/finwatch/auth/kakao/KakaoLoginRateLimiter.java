package com.finwatch.auth.kakao;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.finwatch.auth.session.AuthHashing;
import com.finwatch.auth.session.SessionUnavailableException;

@Component
public class KakaoLoginRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final int requestsPerMinute;

    public KakaoLoginRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.auth.kakao.requests-per-minute}") int requestsPerMinute) {
        this.redisTemplate = redisTemplate;
        this.requestsPerMinute = requestsPerMinute;
    }

    public void check(String clientAddress) {
        String minute = Long.toString(Instant.now().getEpochSecond() / 60);
        String key = "auth:kakao:rate:" + AuthHashing.sha256(clientAddress + ":" + minute);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(2));
            }
            if (count != null && count > requestsPerMinute) {
                throw new KakaoLoginException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "KAKAO_RATE_LIMITED",
                        "카카오 로그인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
            }
        } catch (KakaoLoginException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SessionUnavailableException("로그인 요청 제한 저장소를 사용할 수 없습니다.", exception);
        }
    }
}
