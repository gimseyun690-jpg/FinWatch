package com.finwatch.data.provider;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class ProviderRestClientFactory {

    private ProviderRestClientFactory() {
    }

    public static RestClient create(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        return create(baseUrl, connectTimeout, readTimeout, HttpClient.Redirect.NEVER);
    }

    public static RestClient createFollowingRedirects(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        return create(baseUrl, connectTimeout, readTimeout, HttpClient.Redirect.NORMAL);
    }

    private static RestClient create(
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout,
            HttpClient.Redirect redirectPolicy) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(redirectPolicy)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
