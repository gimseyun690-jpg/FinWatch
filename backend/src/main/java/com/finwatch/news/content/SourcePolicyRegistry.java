package com.finwatch.news.content;

import java.net.IDN;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class SourcePolicyRegistry implements SourcePolicyResolver {

    private final SourcePolicyRepository sourcePolicyRepository;
    private final ConfiguredSourcePolicyProperties configuredPolicies;

    public SourcePolicyRegistry(
            SourcePolicyRepository sourcePolicyRepository,
            ConfiguredSourcePolicyProperties configuredPolicies) {
        this.sourcePolicyRepository = sourcePolicyRepository;
        this.configuredPolicies = configuredPolicies;
    }

    @Override
    public SourcePolicyDecision resolve(URI uri) {
        String host = normalizeHost(uri == null ? null : uri.getHost());
        String path = uri == null || uri.getRawPath() == null || uri.getRawPath().isBlank()
                ? "/"
                : uri.getRawPath();
        if (host.isBlank()) {
            return SourcePolicyDecision.metadataOnly("");
        }

        return Stream.concat(databaseDecisions(), configuredDecisions())
                .filter(policy -> normalizeHost(policy.host()).equals(host))
                .filter(policy -> path.startsWith(normalizePath(policy.pathPrefix())))
                .max(Comparator.comparingInt(policy -> normalizePath(policy.pathPrefix()).length()))
                .orElseGet(() -> SourcePolicyDecision.metadataOnly(host));
    }

    private Stream<SourcePolicyDecision> databaseDecisions() {
        return sourcePolicyRepository.findAllByEnabledTrue().stream().map(this::toDecision);
    }

    private Stream<SourcePolicyDecision> configuredDecisions() {
        List<ConfiguredSourcePolicyProperties.Policy> policies = configuredPolicies.getConfiguredPolicies();
        return policies.stream()
                .filter(ConfiguredSourcePolicyProperties.Policy::isEnabled)
                .filter(policy -> policy.getReviewedAt() != null)
                .filter(policy -> policy.getReviewReference() != null && !policy.getReviewReference().isBlank())
                .map(policy -> new SourcePolicyDecision(
                        normalizeHost(policy.getHost()),
                        normalizePath(policy.getPathPrefix()),
                        policy.getFetchMode(),
                        policy.getRightsProfile(),
                        policy.getContentSource(),
                        Math.max(0, policy.getMinIntervalMs()),
                        policy.isUserAgentRequired(),
                        policy.getReviewReference(),
                        policy.getReviewedAt()));
    }

    private SourcePolicyDecision toDecision(SourcePolicy policy) {
        return new SourcePolicyDecision(
                normalizeHost(policy.getHost()),
                normalizePath(policy.getPathPrefix()),
                policy.getFetchMode(),
                policy.getRightsProfile(),
                policy.getContentSource(),
                policy.getMinIntervalMs(),
                policy.isUserAgentRequired(),
                policy.getReviewReference(),
                policy.getReviewedAt());
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "";
        }
        return IDN.toASCII(host.trim()).toLowerCase(Locale.ROOT);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
