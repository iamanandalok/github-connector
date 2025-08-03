package com.github_connector.github_connector.service;

import com.github_connector.github_connector.config.GitHubProperties;
import com.github_connector.github_connector.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitHubConnectorService {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubConnectorService.class);

    private static final int MAX_RETRY_ATTEMPTS = 4;
    private static final long BASE_BACKOFF_MS = TimeUnit.SECONDS.toMillis(5);  // initial back-off 5s
    private static final long MAX_BACKOFF_MS = TimeUnit.MINUTES.toMillis(2);   // cap at 2 min (reduced from 5)
    private static final int DEFAULT_MAX_REPOS = 20;                           // process at most 20 repos by default
    private static final int MAX_COMMITS_PER_REPO = 20;                        // max 20 commits per repository
    private static final Pattern NEXT_LINK_PATTERN = Pattern.compile("<([^>]*)>; rel=\"next\"");

    private final RestTemplate rest;
    private final GitHubProperties props;

    public GitHubConnectorService(GitHubProperties props,
                                  RestTemplateBuilder builder) {
        this.props = props;
        this.rest = builder
                .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + props.getToken())
                .requestFactory(() -> {
                    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
                    factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
                    return factory;
                })
                .build();
    }

    public List<RepositoryInfo> fetchAllRepos(String userOrOrg) {
        List<RepositoryInfo> repos = new ArrayList<>();
        int page = 1;
        int maxRepos = props.getMaxRepos() > 0 ? props.getMaxRepos() : DEFAULT_MAX_REPOS;
        
        LOG.info("Fetching up to {} repositories for {}", maxRepos, userOrOrg);

        while (repos.size() < maxRepos) {
            String url = String.format("%s/users/%s/repos?per_page=%d&page=%d",
                    props.getApiBaseUrl(),
                    userOrOrg,
                    props.getReposPageSize(),
                    page);

            try {
                ResponseEntity<RepositoryInfo[]> response =
                        rest.getForEntity(url, RepositoryInfo[].class);

                if (!response.getStatusCode().is2xxSuccessful()) {
                    LOG.warn("Non-successful status {} while fetching repos for {}", response.getStatusCode(), userOrOrg);
                    break;
                }

                RepositoryInfo[] batch = response.getBody();
                if (batch == null || batch.length == 0) {
                    break;
                }

                // Add only up to maxRepos repositories
                int remainingCapacity = maxRepos - repos.size();
                if (batch.length <= remainingCapacity) {
                    repos.addAll(Arrays.asList(batch));
                } else {
                    repos.addAll(Arrays.asList(batch).subList(0, remainingCapacity));
                    LOG.info("Reached maximum repository limit ({}) for {}", maxRepos, userOrOrg);
                    break;
                }
                
                page++;
            } catch (HttpClientErrorException e) {
                HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
                
                if (status == HttpStatus.TOO_MANY_REQUESTS || status == HttpStatus.FORBIDDEN) {
                    logRateLimitInfo(e, "repositories", userOrOrg);
                    long waitMillis = computeWaitMillis(e, 0);
                    LOG.warn("Rate limited while fetching repositories for {} – waiting {} ms", 
                             userOrOrg, waitMillis);
                    
                    if (waitMillis > props.getMaxWaitTimeMs()) {
                        LOG.warn("Wait time exceeds maximum allowed ({}ms), aborting repository fetch", 
                                props.getMaxWaitTimeMs());
                        break;
                    }
                    
                    sleepSilently(waitMillis);
                    continue;
                }
                
                LOG.error("Error fetching repositories for {}: {}", userOrOrg, e.getMessage());
                break;
            }
        }

        LOG.info("Fetched {} repositories for {}", repos.size(), userOrOrg);
        return repos;
    }

    public List<CommitActivity> fetchCommits(String owner, String repoName) {
        List<CommitActivity> allCommits = new ArrayList<>();
        int page = 1;
        int attempt = 0;
        String url = String.format("%s/repos/%s/%s/commits?per_page=%d",
                props.getApiBaseUrl(), owner, repoName, props.getCommitsPageSize());
        
        LOG.debug("Fetching up to {} commits for repository {}/{}", MAX_COMMITS_PER_REPO, owner, repoName);
        
        while (allCommits.size() < MAX_COMMITS_PER_REPO) {
            try {
                LOG.debug("Fetching commits page {} for {}/{}", page, owner, repoName);
                ResponseEntity<CommitInfo[]> response = rest.getForEntity(url, CommitInfo[].class);

                if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                    LOG.warn("Repository {}/{} not found (404)", owner, repoName);
                    return Collections.emptyList();
                }

                if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
                    LOG.warn("Received status {} when fetching commits for {}/{}", response.getStatusCode(), owner, repoName);
                    return Collections.emptyList();
                }

                CommitInfo[] body = response.getBody();
                if (body == null || body.length == 0) {
                    LOG.debug("No more commits found for {}/{} on page {}", owner, repoName, page);
                    break;
                }
                
                // Process this batch of commits
                int beforeSize = allCommits.size();
                for (CommitInfo info : body) {
                    if (allCommits.size() >= MAX_COMMITS_PER_REPO) {
                        break;  // Stop if we've reached the maximum
                    }
                    
                    CommitDetail detail = info.getCommitDetail();
                    Author author = detail.getAuthor();
                    allCommits.add(new CommitActivity(
                            detail.getMessage(),
                            author.getName(),
                            author.getDate()));
                }
                
                int newCommitsAdded = allCommits.size() - beforeSize;
                LOG.debug("Received {} commits for {}/{} on page {} (total: {})", 
                         newCommitsAdded, owner, repoName, page, allCommits.size());
                
                // Check if we've reached our limit
                if (allCommits.size() >= MAX_COMMITS_PER_REPO) {
                    LOG.debug("Reached maximum commit limit ({}) for {}/{}", MAX_COMMITS_PER_REPO, owner, repoName);
                    break;
                }
                
                // Check if there's a next page via Link header
                String linkHeader = response.getHeaders().getFirst("Link");
                if (linkHeader == null) {
                    LOG.debug("No Link header found for {}/{} - no more pages", owner, repoName);
                    break;
                }
                
                // Parse the Link header to get the next page URL
                String nextPageUrl = extractNextPageUrl(linkHeader);
                if (nextPageUrl == null) {
                    LOG.debug("No next page link found for {}/{}", owner, repoName);
                    break;
                }
                
                // Update URL and page counter for next iteration
                url = nextPageUrl;
                page++;
                LOG.debug("Following next page link (page {}) for {}/{}", page, owner, repoName);
                
            } catch (HttpClientErrorException e) {
                // Convert HttpStatusCode to HttpStatus
                HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
                
                if (status == HttpStatus.CONFLICT) {          // empty repo
                    LOG.debug("Repository {}/{} is empty (409 Conflict)", owner, repoName);
                    return Collections.emptyList();
                }

                if (status == HttpStatus.TOO_MANY_REQUESTS || status == HttpStatus.FORBIDDEN) {
                    // Forbidden can also mean secondary rate-limit or abuse detection
                    logRateLimitInfo(e, "commits", owner + "/" + repoName);
                    long waitMillis = computeWaitMillis(e, attempt);
                    
                    // Check if wait time exceeds the configured maximum
                    if (waitMillis > props.getMaxWaitTimeMs()) {
                        LOG.warn("Required wait time {}ms exceeds maximum allowed {}ms, skipping repository {}/{}",
                                waitMillis, props.getMaxWaitTimeMs(), owner, repoName);
                        // Return what we have so far, even if it's less than 20
                        return allCommits;
                    }
                    
                    LOG.warn("Rate limited while fetching commits for {}/{} – retrying in {} ms (attempt {}/{})",
                             owner, repoName, waitMillis, attempt + 1, MAX_RETRY_ATTEMPTS);
                    sleepSilently(waitMillis);
                    if (++attempt < MAX_RETRY_ATTEMPTS) {
                        continue;
                    }
                    LOG.error("Exceeded max retry attempts when fetching commits for {}/{}", owner, repoName);
                    // Return what we have so far, even if it's less than 20
                    return allCommits;
                }

                if (status == HttpStatus.NOT_FOUND) {
                    LOG.warn("Repository {}/{} not found (404)", owner, repoName);
                    return Collections.emptyList();
                }
                
                LOG.error("Error fetching commits for {}/{}: {}", owner, repoName, e.getMessage());
                // Return what we have so far, even if it's less than 20
                return allCommits;
            } catch (Exception e) {
                LOG.error("Unexpected error fetching commits for {}/{}: {}", owner, repoName, e.getMessage());
                // Return what we have so far, even if it's less than 20
                return allCommits;
            }
        }
        
        LOG.debug("Completed fetching commits for {}/{} - collected {} commits", 
                 owner, repoName, allCommits.size());
        return allCommits;
    }
    
    /** Get the URL for the “next” page from a GitHub Link header (or {@code null}). */
    private String extractNextPageUrl(String linkHeader) {
        if (linkHeader == null) {
            return null;
        }
        
        Matcher matcher = NEXT_LINK_PATTERN.matcher(linkHeader);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }

    public List<RepoActivity> fetchActivity(String userOrOrg) {
        List<RepoActivity> allActivity = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        long timeoutMs = props.getRequestTimeoutMs();
        
        LOG.info("Starting activity fetch for {} with timeout of {}ms", userOrOrg, timeoutMs);
        
        List<RepositoryInfo> repos = fetchAllRepos(userOrOrg);
        int processedRepos = 0;

        for (RepositoryInfo repo : repos) {
            // Check if we've exceeded the timeout
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (timeoutMs > 0 && elapsedTime > timeoutMs) {
                LOG.warn("Request timeout reached after {}ms while processing repositories for {}, " +
                         "processed {}/{} repos", elapsedTime, userOrOrg, processedRepos, repos.size());
                break;
            }
            
            String[] parts = repo.getFullName().split("/");
            String owner = parts[0], name = parts[1];
            
            LOG.debug("Fetching commits for repository {}/{} ({}/{})", 
                     owner, name, ++processedRepos, repos.size());

            List<CommitActivity> commits = fetchCommits(owner, name);
            RepoActivity activity = new RepoActivity(repo.getName(), commits);
            allActivity.add(activity);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        LOG.info("Completed activity fetch for {} in {}ms - processed {}/{} repositories", 
                userOrOrg, totalTime, processedRepos, repos.size());

        return allActivity;
    }

    /** Call the `/rate_limit` endpoint and return current GitHub API limits. */
    public RateLimitInfo fetchRateLimitInfo() {
        String url = props.getApiBaseUrl() + "/rate_limit";
        LOG.debug("Fetching GitHub API rate limit status from {}", url);
        
        try {
            ResponseEntity<Map> response = rest.getForEntity(url, Map.class);
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                LOG.warn("Failed to fetch rate limit info: {}", response.getStatusCode());
                RateLimitInfo info = new RateLimitInfo();
                info.setMessage("Failed to fetch rate limit information: " + response.getStatusCode());
                return info;
            }
            
            Map<String, Object> body = response.getBody();
            Map<String, Object> resources = (Map<String, Object>) body.get("resources");
            
            RateLimitInfo rateLimitInfo = new RateLimitInfo();
            
            // Process each resource type (core, search, graphql, etc.)
            if (resources != null) {
                processResourceLimits(rateLimitInfo, resources, "core");
                processResourceLimits(rateLimitInfo, resources, "search");
                processResourceLimits(rateLimitInfo, resources, "graphql");
                processResourceLimits(rateLimitInfo, resources, "integration_manifest");
                processResourceLimits(rateLimitInfo, resources, "source_import");
                processResourceLimits(rateLimitInfo, resources, "code_scanning_upload");
                processResourceLimits(rateLimitInfo, resources, "actions_runner_registration");
                processResourceLimits(rateLimitInfo, resources, "scim");
            }
            
            return rateLimitInfo;
            
        } catch (Exception e) {
            LOG.error("Error fetching GitHub API rate limit status", e);
            RateLimitInfo info = new RateLimitInfo();
            info.setMessage("Error fetching rate limit information: " + e.getMessage());
            return info;
        }
    }
    
    /** Quick call to `/user` to verify the configured PAT is valid. */
    public TokenTestResult testToken() {
        String url = props.getApiBaseUrl() + "/user";
        LOG.debug("Testing GitHub token against {}", url);

        try {
            ResponseEntity<Map> response = rest.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.hasBody()) {
                Map body = response.getBody();
                String login = body != null ? String.valueOf(body.get("login")) : null;
                return new TokenTestResult(login,
                        "GitHub token is valid. Authenticated as '" + login + "'.");
            }

            return new TokenTestResult(false, response.getStatusCode().toString(),
                    "Unexpected status when validating token: " + response.getStatusCode());

        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());

            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                return new TokenTestResult(false, status.toString(),
                        "GitHub token is invalid, expired or lacks required scopes.");
            }

            return new TokenTestResult(false, status.toString(),
                    "Error validating token: " + e.getMessage());
        } catch (Exception ex) {
            LOG.error("Unexpected error while validating GitHub token", ex);
            return new TokenTestResult(false, "UNKNOWN_ERROR",
                    "Unexpected error: " + ex.getMessage());
        }
    }

    /** Populate {@link RateLimitInfo} for a single resource section (core, search, …). */
    private void processResourceLimits(RateLimitInfo rateLimitInfo, Map<String, Object> resources, String resourceName) {
        Map<String, Object> resource = (Map<String, Object>) resources.get(resourceName);
        if (resource != null) {
            ResourceLimits limits = new ResourceLimits();
            
            // Extract rate limit details
            if (resource.containsKey("limit")) {
                limits.setLimit(((Number) resource.get("limit")).intValue());
            }
            if (resource.containsKey("remaining")) {
                limits.setRemaining(((Number) resource.get("remaining")).intValue());
            }
            if (resource.containsKey("used")) {
                limits.setUsed(((Number) resource.get("used")).intValue());
            }
            if (resource.containsKey("reset")) {
                long resetTime = ((Number) resource.get("reset")).longValue();
                limits.setReset(resetTime);
                
                // Format the reset time for human readability
                String resetTimeFormatted = new Date(resetTime * 1000).toString();
                limits.setResetTimeFormatted(resetTimeFormatted);
            }
            
            // Set the appropriate field based on resource name
            switch (resourceName) {
                case "core":
                    rateLimitInfo.setCore(limits);
                    break;
                case "search":
                    rateLimitInfo.setSearch(limits);
                    break;
                case "graphql":
                    rateLimitInfo.setGraphql(limits);
                    break;
                case "integration_manifest":
                    rateLimitInfo.setIntegrationManifest(limits);
                    break;
                case "source_import":
                    rateLimitInfo.setSourceImport(limits);
                    break;
                case "code_scanning_upload":
                    rateLimitInfo.setCodeScanningUpload(limits);
                    break;
                case "actions_runner_registration":
                    rateLimitInfo.setActionsRunnerRegistration(limits);
                    break;
                case "scim":
                    rateLimitInfo.setScim(limits);
                    break;
            }
        }
    }

    /** Compute wait milliseconds using X-RateLimit-Reset header if present, otherwise exponential back-off. */
    private long computeWaitMillis(HttpClientErrorException e, int attempt) {
        String reset = e.getResponseHeaders() != null
                ? e.getResponseHeaders().getFirst("X-RateLimit-Reset")
                : null;
        if (reset != null) {
            long waitSec = Long.parseLong(reset) - Instant.now().getEpochSecond();
            return TimeUnit.SECONDS.toMillis(Math.max(waitSec, 1L));
        }
        // exponential back-off
        long backoff = (long) (BASE_BACKOFF_MS * Math.pow(2, attempt));
        return Math.min(MAX_BACKOFF_MS, backoff); // cap at 2 min (reduced from 5)
    }
    
    /**
     * Log detailed rate limit information from GitHub API response headers.
     */
    private void logRateLimitInfo(HttpClientErrorException e, String resourceType, String target) {
        if (e.getResponseHeaders() != null) {
            String remaining = e.getResponseHeaders().getFirst("X-RateLimit-Remaining");
            String limit = e.getResponseHeaders().getFirst("X-RateLimit-Limit");
            String reset = e.getResponseHeaders().getFirst("X-RateLimit-Reset");
            String resource = e.getResponseHeaders().getFirst("X-RateLimit-Resource");
            
            if (reset != null) {
                long resetTime = Long.parseLong(reset);
                long waitSec = resetTime - Instant.now().getEpochSecond();
                String resetTimeStr = new Date(resetTime * 1000).toString();
                
                LOG.warn("GitHub API rate limit hit: {}/{} requests remaining for {} ({}). " +
                         "Limit resets in {} seconds at {}", 
                         remaining, limit, resource != null ? resource : resourceType, target,
                         waitSec, resetTimeStr);
            } else {
                LOG.warn("GitHub API rate limit hit for {} ({}): {}/{} requests remaining", 
                        resourceType, target, remaining, limit);
            }
        }
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
