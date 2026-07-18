package com.finwatch.news.content;

import java.net.URI;
import java.util.Map;

public interface ArticleHttpTransport {

    ArticleHttpResponse get(URI uri, String userAgent, Map<String, String> requestHeaders);
}
