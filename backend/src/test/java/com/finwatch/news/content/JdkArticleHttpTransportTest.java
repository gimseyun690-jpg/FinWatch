package com.finwatch.news.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class JdkArticleHttpTransportTest {

    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void acceptsOrdinaryLargeHtmlWithinTwoMiBLimit() {
        byte[] body = new byte[1_500_000];
        serve("/article", body, body.length);

        var response = transport(2_097_152).get(uri("/article"), "FinWatch-Test/1.0", Map.of());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).hasSize(body.length);
    }

    @Test
    void rejectsDeclaredOversizeAndStillAllowsTheNextRequest() {
        serve("/large", new byte[1_024], 1_024);
        serve("/small", new byte[20], 20);
        var client = transport(512);

        assertThatThrownBy(() -> client.get(uri("/large"), "FinWatch-Test/1.0", Map.of()))
                .isInstanceOf(NewsContentException.class)
                .hasMessage("기사 본문 응답이 허용된 크기를 초과했습니다.");
        assertThat(client.get(uri("/small"), "FinWatch-Test/1.0", Map.of()).body()).hasSize(20);
    }

    @Test
    void alsoRejectsOversizedChunkedResponseWithoutContentLength() {
        serve("/chunked", new byte[1_024], 0);

        assertThatThrownBy(() -> transport(512).get(uri("/chunked"), "FinWatch-Test/1.0", Map.of()))
                .isInstanceOf(NewsContentException.class)
                .hasMessage("기사 본문 응답이 허용된 크기를 초과했습니다.");
    }

    private void serve(String path, byte[] body, long responseLength) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, responseLength);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private JdkArticleHttpTransport transport(int limit) {
        return new JdkArticleHttpTransport(Duration.ofSeconds(2), Duration.ofSeconds(5), limit);
    }
}
