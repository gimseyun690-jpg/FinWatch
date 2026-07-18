package com.finwatch.news.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("demo")
class SourcePolicyRegistryIntegrationTest {

    @Autowired
    private SourcePolicyRegistry sourcePolicyRegistry;

    @Test
    void onlyExplicitSecArchivePathAllowsDirectFetch() {
        SourcePolicyDecision allowed = sourcePolicyRegistry.resolve(URI.create(
                "https://www.sec.gov/Archives/edgar/data/1045810/filing.htm"));
        SourcePolicyDecision otherSecPage = sourcePolicyRegistry.resolve(URI.create(
                "https://www.sec.gov/about/developer-resources"));

        assertThat(allowed.fetchMode()).isEqualTo(SourceFetchMode.ALLOWLIST_FETCH);
        assertThat(allowed.contentSource()).isEqualTo(ContentSource.OFFICIAL_DISCLOSURE);
        assertThat(allowed.userAgentRequired()).isTrue();
        assertThat(otherSecPage.fetchMode()).isEqualTo(SourceFetchMode.METADATA_ONLY);
    }

    @Test
    void unknownDomainDefaultsToMetadataOnly() {
        SourcePolicyDecision decision = sourcePolicyRegistry.resolve(URI.create(
                "https://news.example.org/articles/1"));

        assertThat(decision.fetchMode()).isEqualTo(SourceFetchMode.METADATA_ONLY);
        assertThat(decision.rightsProfile()).isEqualTo(RightsProfile.METADATA_ONLY);
        assertThat(decision.allowsDirectFetch()).isFalse();
    }

    @Test
    void openDartDocumentUsesProviderApiInsteadOfGenericFetcher() {
        SourcePolicyDecision decision = sourcePolicyRegistry.resolve(URI.create(
                "https://opendart.fss.or.kr/api/document.xml?rcept_no=20260713000123"));

        assertThat(decision.fetchMode()).isEqualTo(SourceFetchMode.API_CONTENT);
        assertThat(decision.contentSource()).isEqualTo(ContentSource.OFFICIAL_DISCLOSURE);
        assertThat(decision.allowsDirectFetch()).isFalse();
    }
}
