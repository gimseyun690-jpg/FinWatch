package com.finwatch.news.content;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UrlSafetyValidator {

    private final HostResolver hostResolver;

    public UrlSafetyValidator(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    public URI validate(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw blocked("ARTICLE_URL_SCHEME_BLOCKED", "기사 본문은 HTTPS 주소에서만 수집할 수 있습니다.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            throw blocked("ARTICLE_URL_INVALID", "기사 주소의 호스트 또는 사용자 정보가 올바르지 않습니다.");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw blocked("ARTICLE_URL_PORT_BLOCKED", "기사 본문 수집은 기본 HTTPS 포트만 허용합니다.");
        }

        List<InetAddress> addresses;
        try {
            addresses = hostResolver.resolve(uri.getHost());
        } catch (UnknownHostException exception) {
            throw new NewsContentException(
                    HttpStatus.BAD_GATEWAY,
                    "ARTICLE_HOST_UNRESOLVED",
                    "기사 출처의 호스트를 확인할 수 없습니다.",
                    exception);
        }
        if (addresses.isEmpty() || addresses.stream().anyMatch(this::isBlockedAddress)) {
            throw blocked("ARTICLE_PRIVATE_ADDRESS_BLOCKED", "사설 또는 로컬 네트워크 주소로의 접근은 차단됩니다.");
        }
        return uri.normalize();
    }

    private boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || "169.254.169.254".equals(address.getHostAddress());
    }

    private NewsContentException blocked(String code, String message) {
        return new NewsContentException(HttpStatus.FORBIDDEN, code, message);
    }
}
