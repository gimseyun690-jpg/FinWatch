package com.finwatch.news.content;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ArticleContentFetcher {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/html",
            "application/xhtml+xml",
            "application/xml",
            "text/xml",
            "application/rss+xml",
            "application/atom+xml");

    private final SourcePolicyResolver sourcePolicyResolver;
    private final UrlSafetyValidator urlSafetyValidator;
    private final DomainRateLimiter domainRateLimiter;
    private final ArticleHttpTransport articleHttpTransport;
    private final ArticleContentExtractor contentExtractor;
    private final String configuredUserAgent;
    private final int maxRedirects;
    private final int maxResponseBytes;

    public ArticleContentFetcher(
            SourcePolicyResolver sourcePolicyResolver,
            UrlSafetyValidator urlSafetyValidator,
            DomainRateLimiter domainRateLimiter,
            ArticleHttpTransport articleHttpTransport,
            ArticleContentExtractor contentExtractor,
            @Value("${app.news-content.user-agent:}") String configuredUserAgent,
            @Value("${app.news-content.max-redirects}") int maxRedirects,
            @Value("${app.news-content.max-response-bytes}") int maxResponseBytes) {
        this.sourcePolicyResolver = sourcePolicyResolver;
        this.urlSafetyValidator = urlSafetyValidator;
        this.domainRateLimiter = domainRateLimiter;
        this.articleHttpTransport = articleHttpTransport;
        this.contentExtractor = contentExtractor;
        this.configuredUserAgent = configuredUserAgent == null ? "" : configuredUserAgent.trim();
        this.maxRedirects = maxRedirects;
        this.maxResponseBytes = maxResponseBytes;
    }

    public FetchedArticleContent fetch(URI canonicalUrl) {
        URI currentUrl = canonicalUrl;
        Set<String> rateLimitedHosts = new HashSet<>();

        for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
            SourcePolicyDecision policy = authorize(currentUrl);
            URI safeUrl = urlSafetyValidator.validate(currentUrl);
            String userAgent = resolveUserAgent(policy);
            if (rateLimitedHosts.add(safeUrl.getHost())) {
                domainRateLimiter.check(safeUrl.getHost(), policy.minIntervalMs());
            }

            ArticleHttpResponse response = articleHttpTransport.get(safeUrl, userAgent, Map.of());
            if (isRedirect(response.statusCode())) {
                if (redirectCount == maxRedirects) {
                    throw new NewsContentException(
                            HttpStatus.BAD_GATEWAY,
                            "ARTICLE_REDIRECT_LIMIT_EXCEEDED",
                            "기사 출처의 리다이렉트 횟수가 허용 범위를 초과했습니다.");
                }
                String location = response.firstHeader("Location")
                        .orElseThrow(() -> new NewsContentException(
                                HttpStatus.BAD_GATEWAY,
                                "ARTICLE_REDIRECT_INVALID",
                                "기사 출처가 유효한 리다이렉트 주소를 제공하지 않았습니다."));
                currentUrl = safeUrl.resolve(location);
                continue;
            }
            if (response.statusCode() == 429) {
                throw new NewsContentException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "PROVIDER_RATE_LIMITED",
                        "기사 출처가 호출 한도를 초과했다고 응답했습니다.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new NewsContentException(
                        HttpStatus.BAD_GATEWAY,
                        "ARTICLE_FETCH_FAILED",
                        "기사 출처가 정상 응답을 반환하지 않았습니다.");
            }
            if (response.body().length > maxResponseBytes) {
                throw new NewsContentException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "ARTICLE_RESPONSE_TOO_LARGE",
                        "기사 본문 응답이 허용된 크기를 초과했습니다.");
            }

            String contentType = normalizeContentType(response.firstHeader("Content-Type").orElse(""));
            if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
                throw new NewsContentException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "ARTICLE_CONTENT_TYPE_BLOCKED",
                        "허용되지 않은 기사 본문 형식입니다.");
            }
            ArticleContentExtractor.ExtractedContent extracted = contentExtractor.extract(
                    response.body(), contentType, safeUrl.toString());
            return new FetchedArticleContent(
                    canonicalUrl,
                    safeUrl,
                    extracted.title(),
                    extracted.text(),
                    contentType,
                    Instant.now(),
                    response.firstHeader("ETag").orElse(null),
                    response.firstHeader("Last-Modified").orElse(null),
                    extracted.contentHash(),
                    ArticleContentExtractor.EXTRACTOR_VERSION,
                    policy.contentSource(),
                    policy.rightsProfile());
        }
        throw new IllegalStateException("Redirect loop terminated unexpectedly.");
    }

    private SourcePolicyDecision authorize(URI uri) {
        SourcePolicyDecision policy = sourcePolicyResolver.resolve(uri);
        if (!policy.allowsDirectFetch()) {
            throw NewsContentException.unavailable();
        }
        return policy;
    }

    private String resolveUserAgent(SourcePolicyDecision policy) {
        if (policy.userAgentRequired() && configuredUserAgent.isBlank()) {
            throw new NewsContentException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ARTICLE_USER_AGENT_REQUIRED",
                    "이 출처를 수집하려면 프로젝트명과 연락처가 포함된 User-Agent 설정이 필요합니다.");
        }
        return configuredUserAgent.isBlank() ? "FinWatch/0.1" : configuredUserAgent;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    private String normalizeContentType(String contentType) {
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
