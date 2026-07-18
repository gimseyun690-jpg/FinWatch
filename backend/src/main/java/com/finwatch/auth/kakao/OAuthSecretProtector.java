package com.finwatch.auth.kakao;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class OAuthSecretProtector {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] AAD = "finwatch-kakao-pkce-v1".getBytes(StandardCharsets.UTF_8);
    private final SecretKey encryptionKey;

    public OAuthSecretProtector(SecretKey jwtSecretKey) {
        byte[] source = jwtSecretKey.getEncoded();
        byte[] key = new byte[32];
        System.arraycopy(source, 0, key, 0, Math.min(source.length, key.length));
        this.encryptionKey = new SecretKeySpec(key, "AES");
    }

    public String protect(String plaintext) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (Exception exception) {
            throw new IllegalStateException("OAuth verifier protection failed.", exception);
        }
    }

    public String unprotect(String protectedValue) {
        try {
            byte[] packed = Base64.getUrlDecoder().decode(protectedValue);
            if (packed.length < 29) {
                throw new IllegalArgumentException("Protected value is too short.");
            }
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[packed.length - iv.length];
            System.arraycopy(packed, 0, iv, 0, iv.length);
            System.arraycopy(packed, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new KakaoLoginException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "KAKAO_ATTEMPT_INVALID",
                    "카카오 로그인 요청을 확인할 수 없습니다.",
                    exception);
        }
    }
}
