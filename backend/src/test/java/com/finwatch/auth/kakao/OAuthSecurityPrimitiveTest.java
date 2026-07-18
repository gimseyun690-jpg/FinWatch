package com.finwatch.auth.kakao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.finwatch.auth.session.AuthHashing;

class OAuthSecurityPrimitiveTest {

    @Test
    void createsUniqueHighEntropyTokensAndRfc7636Challenge() {
        String first = AuthHashing.randomUrlToken(32);
        String second = AuthHashing.randomUrlToken(32);

        assertThat(first).isNotEqualTo(second).hasSizeGreaterThanOrEqualTo(43);
        assertThat(second).doesNotContain("=", "+", "/");
        assertThat(OAuthAttemptService.pkceChallenge(
                "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void protectsPkceVerifierAtRest() {
        byte[] key = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        OAuthSecretProtector protector = new OAuthSecretProtector(new SecretKeySpec(key, "HmacSHA256"));

        String protectedValue = protector.protect("server-only-code-verifier");

        assertThat(protectedValue).doesNotContain("server-only-code-verifier");
        assertThat(protector.unprotect(protectedValue)).isEqualTo("server-only-code-verifier");
    }

    @Test
    void onlyAllowsInternalNonAdminReturnRoutes() {
        KakaoReturnToValidator validator = new KakaoReturnToValidator();

        assertThat(validator.validate("/stocks/KRX/000660/news?tab=latest"))
                .isEqualTo("/stocks/KRX/000660/news?tab=latest");
        assertThat(validator.validate("https://evil.example/steal")).isEqualTo("/dashboard");
        assertThat(validator.validate("//evil.example/steal")).isEqualTo("/dashboard");
        assertThat(validator.validate("/admin/ai")).isEqualTo("/dashboard");
        assertThat(validator.validate("/news\\evil")).isEqualTo("/dashboard");
    }
}
