package com.finwatch.news.content;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.news-content")
public class ConfiguredSourcePolicyProperties {

    private List<Policy> configuredPolicies = new ArrayList<>();

    public List<Policy> getConfiguredPolicies() {
        return configuredPolicies;
    }

    public void setConfiguredPolicies(List<Policy> configuredPolicies) {
        this.configuredPolicies = configuredPolicies == null ? new ArrayList<>() : configuredPolicies;
    }

    public static class Policy {
        private String host = "";
        private String pathPrefix = "/";
        private SourceFetchMode fetchMode = SourceFetchMode.METADATA_ONLY;
        private RightsProfile rightsProfile = RightsProfile.METADATA_ONLY;
        private ContentSource contentSource = ContentSource.METADATA_ONLY;
        private int minIntervalMs;
        private boolean userAgentRequired;
        private boolean enabled;
        private String reviewReference = "";
        private LocalDate reviewedAt;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix; }
        public SourceFetchMode getFetchMode() { return fetchMode; }
        public void setFetchMode(SourceFetchMode fetchMode) { this.fetchMode = fetchMode; }
        public RightsProfile getRightsProfile() { return rightsProfile; }
        public void setRightsProfile(RightsProfile rightsProfile) { this.rightsProfile = rightsProfile; }
        public ContentSource getContentSource() { return contentSource; }
        public void setContentSource(ContentSource contentSource) { this.contentSource = contentSource; }
        public int getMinIntervalMs() { return minIntervalMs; }
        public void setMinIntervalMs(int minIntervalMs) { this.minIntervalMs = minIntervalMs; }
        public boolean isUserAgentRequired() { return userAgentRequired; }
        public void setUserAgentRequired(boolean userAgentRequired) { this.userAgentRequired = userAgentRequired; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getReviewReference() { return reviewReference; }
        public void setReviewReference(String reviewReference) { this.reviewReference = reviewReference; }
        public LocalDate getReviewedAt() { return reviewedAt; }
        public void setReviewedAt(LocalDate reviewedAt) { this.reviewedAt = reviewedAt; }
    }
}
