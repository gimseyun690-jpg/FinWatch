package com.finwatch.news.content;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "source_policies")
public class SourcePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(name = "path_prefix", nullable = false, length = 500)
    private String pathPrefix;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_mode", nullable = false, length = 40)
    private SourceFetchMode fetchMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "rights_profile", nullable = false, length = 40)
    private RightsProfile rightsProfile;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_source", nullable = false, length = 40)
    private ContentSource contentSource;

    @Column(name = "min_interval_ms", nullable = false)
    private int minIntervalMs;

    @Column(name = "user_agent_required", nullable = false)
    private boolean userAgentRequired;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "review_reference", nullable = false, length = 1000)
    private String reviewReference;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDate reviewedAt;

    protected SourcePolicy() {
    }

    public String getHost() {
        return host;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public SourceFetchMode getFetchMode() {
        return fetchMode;
    }

    public RightsProfile getRightsProfile() {
        return rightsProfile;
    }

    public ContentSource getContentSource() {
        return contentSource;
    }

    public int getMinIntervalMs() {
        return minIntervalMs;
    }

    public boolean isUserAgentRequired() {
        return userAgentRequired;
    }

    public String getReviewReference() {
        return reviewReference;
    }

    public LocalDate getReviewedAt() {
        return reviewedAt;
    }
}
