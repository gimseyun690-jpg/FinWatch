package com.finwatch.auth.kakao;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.finwatch.auth.session.AuthHashing;

@Component
public class KakaoIdTokenValidator {

    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final JwtDecoder decoder;
    private final KakaoLoginProperties properties;

    public KakaoIdTokenValidator(KakaoLoginProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(requestFactory))
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                timestampValidator));
        this.decoder = nimbus;
    }

    public KakaoProfile validate(String idToken, String expectedNonceHash) {
        if (idToken == null || idToken.isBlank()) {
            throw invalid("KAKAO_ID_TOKEN_MISSING", "카카오 ID Token이 없습니다.", null);
        }
        try {
            Jwt jwt = decoder.decode(idToken);
            if (!jwt.getAudience().contains(properties.clientId())) {
                throw invalid("KAKAO_ID_TOKEN_AUDIENCE_INVALID", "카카오 ID Token audience가 일치하지 않습니다.", null);
            }
            String nonce = jwt.getClaimAsString("nonce");
            if (nonce == null || !AuthHashing.secureEquals(expectedNonceHash, AuthHashing.sha256(nonce))) {
                throw invalid("KAKAO_ID_TOKEN_NONCE_INVALID", "카카오 ID Token nonce가 일치하지 않습니다.", null);
            }
            Instant issuedAt = jwt.getIssuedAt();
            if (issuedAt == null || issuedAt.isAfter(Instant.now().plus(CLOCK_SKEW))) {
                throw invalid("KAKAO_ID_TOKEN_IAT_INVALID", "카카오 ID Token 발급 시각이 올바르지 않습니다.", null);
            }
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank() || subject.length() > 255) {
                throw invalid("KAKAO_SUBJECT_INVALID", "카카오 사용자 식별자가 없습니다.", null);
            }
            return new KakaoProfile(
                    subject,
                    jwt.getClaimAsString("nickname"),
                    jwt.getClaimAsString("picture"),
                    jwt.getClaimAsString("email"));
        } catch (KakaoLoginException exception) {
            throw exception;
        } catch (JwtException exception) {
            if (providerUnavailable(exception)) {
                throw new KakaoLoginException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "KAKAO_TOKEN_UNAVAILABLE",
                        "카카오 인증 키 서버를 일시적으로 사용할 수 없습니다.",
                        exception);
            }
            throw invalid("KAKAO_ID_TOKEN_INVALID", "카카오 ID Token을 검증할 수 없습니다.", exception);
        }
    }

    private boolean providerUnavailable(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ResourceAccessException) return true;
            if (current instanceof RestClientResponseException response
                    && (response.getStatusCode().is5xxServerError()
                            || response.getStatusCode().value() == 429)) {
                return true;
            }
        }
        return false;
    }

    private KakaoLoginException invalid(String code, String message, Throwable cause) {
        return cause == null
                ? new KakaoLoginException(HttpStatus.UNAUTHORIZED, code, message)
                : new KakaoLoginException(HttpStatus.UNAUTHORIZED, code, message, cause);
    }

    public record KakaoProfile(String subject, String nickname, String picture, String email) {
    }
}
