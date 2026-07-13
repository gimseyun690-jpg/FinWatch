package com.finwatch.news.content;

import java.net.URI;

public interface SourcePolicyResolver {

    SourcePolicyDecision resolve(URI uri);
}
