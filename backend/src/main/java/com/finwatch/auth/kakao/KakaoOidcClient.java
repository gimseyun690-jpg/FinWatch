package com.finwatch.auth.kakao;

import java.net.http.HttpClient;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.finwatch.auth.kakao.KakaoIdTokenValidator.KakaoProfile;

@Component
public class KakaoOidcClient {

    private final KakaoLoginProperties properties;
    private final KakaoIdTokenValidator idTokenValidator;
    private final RestClient restClient;

    public KakaoOidcClient(
            KakaoLoginProperties properties,
            KakaoIdTokenValidator idTokenValidator) {
        this.properties = properties;
        this.idTokenValidator = idTokenValidator;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public KakaoProfile exchange(String code, String codeVerifier, String expectedNonceHash) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);
        form.add("code_verifier", codeVerifier);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            String idToken = response == null || response.get("id_token") == null
                    ? null
                    : response.get("id_token").toString();
            return idTokenValidator.validate(idToken, expectedNonceHash);
        } catch (RestClientResponseException exception) {
            throw new KakaoLoginException(
                    HttpStatus.BAD_GATEWAY,
                    "KAKAO_TOKEN_REJECTED",
                    "카카오가 인가 코드 교환을 거부했습니다.",
                    exception);
        } catch (ResourceAccessException exception) {
            throw new KakaoLoginException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "KAKAO_TOKEN_UNAVAILABLE",
                    "카카오 인증 서버에 연결할 수 없습니다.",
                    exception);
        }
    }
}
