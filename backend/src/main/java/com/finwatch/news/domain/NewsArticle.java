package com.finwatch.news.domain;

import java.time.Instant;
import java.util.Objects;

import com.finwatch.news.content.ContentSource;
import com.finwatch.news.content.FetchedArticleContent;
import com.finwatch.news.content.RightsProfile;
import com.finwatch.stock.domain.Stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "news_articles")
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 150)
    private String publisher;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "canonical_url", length = 1000)
    private String canonicalUrl;

    @Column(name = "final_url", length = 1000)
    private String finalUrl;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(nullable = false, length = 50)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_source", nullable = false, length = 40)
    private ContentSource contentSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "rights_profile", nullable = false, length = 40)
    private RightsProfile rightsProfile;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "extractor_version", length = 50)
    private String extractorVersion;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NewsArticle() {
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public Stock getStock() {
        return stock;
    }

    public String getTitle() {
        return title;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getUrl() {
        return url;
    }

    public String getCanonicalUrl() {
        return canonicalUrl == null ? url : canonicalUrl;
    }

    public String getFinalUrl() {
        return finalUrl == null ? getCanonicalUrl() : finalUrl;
    }

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }

    public ContentSource getContentSource() {
        return contentSource;
    }

    public RightsProfile getRightsProfile() {
        return rightsProfile;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getExtractorVersion() {
        return extractorVersion;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public boolean isAiAnalysisAllowed() {
        return rightsProfile != null
                && rightsProfile.isAiAnalysisAllowed()
                && contentSource != ContentSource.METADATA_ONLY
                && content != null
                && !content.isBlank();
    }

    public boolean applyFetchedContent(FetchedArticleContent fetchedContent) {
        boolean changed = !Objects.equals(contentHash, fetchedContent.contentHash());
        content = fetchedContent.extractedText();
        canonicalUrl = fetchedContent.canonicalUrl().toString();
        finalUrl = fetchedContent.finalUrl().toString();
        contentSource = fetchedContent.contentSource();
        rightsProfile = fetchedContent.rightsProfile();
        contentHash = fetchedContent.contentHash();
        extractorVersion = fetchedContent.extractorVersion();
        fetchedAt = fetchedContent.fetchedAt();
        updatedAt = Instant.now();
        return changed;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
