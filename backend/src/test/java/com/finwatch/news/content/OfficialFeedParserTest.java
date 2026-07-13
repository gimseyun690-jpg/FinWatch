package com.finwatch.news.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class OfficialFeedParserTest {

    private final OfficialFeedParser parser = new OfficialFeedParser();

    @Test
    void parsesRssAndRemovesEmbeddedMarkup() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel><item>
                  <guid>release-1</guid>
                  <title>공식 실적 발표</title>
                  <link>https://ir.example.com/releases/1</link>
                  <description><![CDATA[<p>매출이 증가했습니다.</p><script>ignore()</script>]]></description>
                  <pubDate>Mon, 13 Jul 2026 01:00:00 GMT</pubDate>
                </item></channel></rss>
                """;

        var entries = parser.parse(rss.getBytes(StandardCharsets.UTF_8), URI.create("https://ir.example.com/rss"));

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().externalId()).isEqualTo("release-1");
        assertThat(entries.getFirst().description()).contains("매출이 증가했습니다").doesNotContain("ignore");
        assertThat(entries.getFirst().publishedAt()).isEqualTo(Instant.parse("2026-07-13T01:00:00Z"));
    }

    @Test
    void parsesAtomAndRejectsNonHttpsLinks() {
        String atom = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry><id>a-1</id><title>허용 항목</title><link href="/news/1"/><updated>2026-07-13T02:00:00Z</updated></entry>
                  <entry><id>a-2</id><title>차단 항목</title><link href="javascript:alert(1)"/></entry>
                </feed>
                """;

        var entries = parser.parse(atom.getBytes(StandardCharsets.UTF_8), URI.create("https://ir.example.com/feed"));

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().url()).isEqualTo(URI.create("https://ir.example.com/news/1"));
    }
}
