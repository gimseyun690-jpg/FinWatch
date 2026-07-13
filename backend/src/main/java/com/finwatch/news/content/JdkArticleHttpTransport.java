package com.finwatch.news.content;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JdkArticleHttpTransport implements ArticleHttpTransport {

    private final HttpClient httpClient;
    private final Duration readTimeout;
    private final int maxResponseBytes;

    public JdkArticleHttpTransport(
            @Value("${app.news-content.connect-timeout}") Duration connectTimeout,
            @Value("${app.news-content.read-timeout}") Duration readTimeout,
            @Value("${app.news-content.max-response-bytes}") int maxResponseBytes) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.readTimeout = readTimeout;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public ArticleHttpResponse get(URI uri, String userAgent, Map<String, String> requestHeaders) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(readTimeout)
                .header("Accept", "text/html,application/xhtml+xml,application/xml,text/xml,application/rss+xml,application/atom+xml")
                .header("User-Agent", userAgent)
                .GET();
        requestHeaders.forEach(requestBuilder::header);

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > maxResponseBytes) {
                throw responseTooLarge();
            }
            byte[] body;
            try (InputStream inputStream = response.body()) {
                body = readLimited(inputStream);
            }
            return new ArticleHttpResponse(response.statusCode(), response.headers().map(), body);
        } catch (HttpTimeoutException exception) {
            throw new NewsContentException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "ARTICLE_FETCH_TIMEOUT",
                    "기사 본문 요청 시간이 초과되었습니다.",
                    exception);
        } catch (IOException exception) {
            throw new NewsContentException(
                    HttpStatus.BAD_GATEWAY,
                    "ARTICLE_FETCH_FAILED",
                    "기사 본문을 가져오지 못했습니다.",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NewsContentException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ARTICLE_FETCH_INTERRUPTED",
                    "기사 본문 요청이 중단되었습니다.",
                    exception);
        }
    }

    private byte[] readLimited(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxResponseBytes, 16_384));
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxResponseBytes) {
                throw responseTooLarge();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private NewsContentException responseTooLarge() {
        return new NewsContentException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "ARTICLE_RESPONSE_TOO_LARGE",
                "기사 본문 응답이 허용된 크기를 초과했습니다.");
    }
}
