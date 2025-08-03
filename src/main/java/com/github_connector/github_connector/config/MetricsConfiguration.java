package com.github_connector.github_connector.config;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Meter.Id;  // <- Added missing import
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.regex.Pattern;

/**
 * Fixes GitHub API URI metrics to prevent too many unique tags.
 */
@Configuration
public class MetricsConfiguration {

    private static final Pattern GITHUB_USER_REPOS_PATTERN = 
            Pattern.compile("/users/[^/]+/repos");
    private static final Pattern GITHUB_REPO_COMMITS_PATTERN = 
            Pattern.compile("/repos/[^/]+/[^/]+/commits");

    /**
     * Normalizes GitHub API URIs in metrics.
     */
    @Bean
    public MeterFilter uriTagNormalizationFilter() {
        return new MeterFilter() {
            @Override
            public Id map(Id id) {
                // Only apply to HTTP client request metrics
                if ("http.client.requests".equals(id.getName())) {
                    String uri = id.getTag("uri");
                    if (uri != null) {
                        // Normalize GitHub API paths
                        if (GITHUB_USER_REPOS_PATTERN.matcher(uri).find()) {
                            return id.replaceTags(Tags.of("uri", "/users/{username}/repos"));
                        } else if (GITHUB_REPO_COMMITS_PATTERN.matcher(uri).find()) {
                            return id.replaceTags(Tags.of("uri", "/repos/{owner}/{repo}/commits"));
                        }
                    }
                }
                return id;
            }
        };
    }
}
