package com.github_connector.github_connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {
    /** GitHub API token */
    private String token;

    /** GitHub API base URL */
    private String apiBaseUrl;

    /** Repos per page */
    private int reposPageSize;

    /** Commits per repo */
    private int commitsPageSize;

    /** Max repositories to process */
    private int maxRepos = 20;

    /** Max wait time for rate limits (ms) */
    private long maxWaitTimeMs = 120_000L;

    /** Request timeout (ms) */
    private long requestTimeoutMs = 300_000L;

    // --- getters & setters ---

    public String getToken() {
        return token;
    }
    public void setToken(String token) {
        this.token = token;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }
    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public int getReposPageSize() {
        return reposPageSize;
    }
    public void setReposPageSize(int reposPageSize) {
        this.reposPageSize = reposPageSize;
    }

    public int getCommitsPageSize() {
        return commitsPageSize;
    }
    public void setCommitsPageSize(int commitsPageSize) {
        this.commitsPageSize = commitsPageSize;
    }

    public int getMaxRepos() {
        return maxRepos;
    }
    public void setMaxRepos(int maxRepos) {
        this.maxRepos = maxRepos;
    }

    public long getMaxWaitTimeMs() {
        return maxWaitTimeMs;
    }
    public void setMaxWaitTimeMs(long maxWaitTimeMs) {
        this.maxWaitTimeMs = maxWaitTimeMs;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }
    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }
}
