package com.github_connector.github_connector.controller;

import com.github_connector.github_connector.model.ActivityResponse;
import com.github_connector.github_connector.model.CommitActivity;
import com.github_connector.github_connector.model.Meta;
import com.github_connector.github_connector.model.RateLimitInfo;
import com.github_connector.github_connector.model.RateLimitResponse;
import com.github_connector.github_connector.model.RepoActivity;
import com.github_connector.github_connector.model.RepositoryInfo;
import com.github_connector.github_connector.model.TokenTestResult;
import com.github_connector.github_connector.service.GitHubConnectorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@CrossOrigin // allow basic CORS; tweak origins in config if needed
@RequestMapping("/api/github")
public class GitHubController {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubController.class);

    private final GitHubConnectorService connectorService;

    public GitHubController(GitHubConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    /**
     * Get activity for all repositories of a user/org.
     */
    @GetMapping("/{userOrOrg}")
    public ResponseEntity<?> getActivity(
            @PathVariable String userOrOrg) {
        
        // Basic validation: GitHub usernames may contain alphanumerics or hyphens, max length 39, not start/end with hyphen
        if (!isValidGitHubIdentifier(userOrOrg)) {
            LOG.warn("Invalid GitHub identifier received: '{}'", userOrOrg);
            return ResponseEntity.badRequest().build();
        }

        LOG.info("Request: fetchActivity userOrOrg={}", userOrOrg);
        try {
            List<RepoActivity> allActivities = connectorService.fetchActivity(userOrOrg);

            /* If we received no data, it's very likely that the request was short-circuited
               due to a GitHub rate-limit (see service layer). Return 429 to inform
               clients that they should back-off and retry later instead of treating an
               empty payload as a successful but empty account. */
            if (allActivities.isEmpty()) {
                LOG.warn("No repositories returned for '{}' – probable GitHub rate-limit hit", userOrOrg);
                return ResponseEntity
                        .status(429)
                        .body(new RateLimitResponse(
                                "GitHub API rate limit reached. Please retry after the reset window."));
            }

            ActivityResponse body = buildResponse(allActivities);
            LOG.debug("Response: totalRepos={}, totalCommits={}",
                    body.getMeta().getTotalRepos(), body.getMeta().getTotalCommits());

            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            LOG.error("Error while fetching GitHub activity for '{}'", userOrOrg, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health-check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    /**
     * Get a light-weight summary (metadata only) for a user/org.
     */
    @GetMapping("/{userOrOrg}/summary")
    public ResponseEntity<Meta> getSummary(@PathVariable String userOrOrg) {
        if (!isValidGitHubIdentifier(userOrOrg)) {
            return ResponseEntity.badRequest().build();
        }
        List<RepoActivity> data = connectorService.fetchActivity(userOrOrg);
        return ResponseEntity.ok(buildMeta(data));
    }

    /**
     * Activity for a specific repository.
     */
    @GetMapping("/{userOrOrg}/{repoName}")
    public ResponseEntity<?> getRepoActivity(@PathVariable String userOrOrg,
                                             @PathVariable String repoName) {
        if (!isValidGitHubIdentifier(userOrOrg)) {
            return ResponseEntity.badRequest().build();
        }
        LOG.info("Request: repoActivity {}/{}", userOrOrg, repoName);
        try {
            List<CommitActivity> commits = connectorService.fetchCommits(userOrOrg, repoName);
            return ResponseEntity.ok(commits);
        } catch (Exception ex) {
            LOG.error("Error fetching repo activity for {}/{}", userOrOrg, repoName, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Endpoint to explicitly refresh / bypass cache (no real cache yet).
     * Refresh and return activity data.
     */
    @PostMapping("/{userOrOrg}/refresh")
    public ResponseEntity<?> refresh(
            @PathVariable String userOrOrg) {
        return getActivity(userOrOrg);
    }

    /**
     * Get current GitHub API rate limit status.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getRateLimitStatus() {
        LOG.info("Request: GitHub API rate limit status");
        try {
            RateLimitInfo rateLimitInfo = connectorService.fetchRateLimitInfo();
            return ResponseEntity.ok(rateLimitInfo);
        } catch (Exception ex) {
            LOG.error("Error fetching GitHub API rate limit status", ex);
            return ResponseEntity
                    .status(500)
                    .body(new RateLimitResponse("Unable to fetch GitHub API rate limit status: " + ex.getMessage()));
        }
    }

    /**
     * Test if GitHub token is valid.
     */
    @GetMapping("/test-token")
    public ResponseEntity<?> testToken() {
        LOG.info("Request: Test GitHub token validity");
        try {
            TokenTestResult result = connectorService.testToken();
            if (result.isValid()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity
                        .status(401)
                        .body(result);
            }
        } catch (Exception ex) {
            LOG.error("Error testing GitHub token", ex);
            return ResponseEntity
                    .status(500)
                    .body(new RateLimitResponse("Error testing GitHub token: " + ex.getMessage()));
        }
    }

    /**
     * Quick test endpoint – only processes first repository.
     */
    @GetMapping("/{userOrOrg}/quick")
    public ResponseEntity<?> getQuickActivity(@PathVariable String userOrOrg) {
        if (!isValidGitHubIdentifier(userOrOrg)) {
            LOG.warn("Invalid GitHub identifier received: '{}'", userOrOrg);
            return ResponseEntity.badRequest().build();
        }

        LOG.info("Request: quickActivity userOrOrg={}", userOrOrg);
        try {
            // Fetch repositories (service already enforces max-repos limit)
            List<RepositoryInfo> repos = connectorService.fetchAllRepos(userOrOrg);
            if (repos.isEmpty()) {
                return ResponseEntity
                        .status(429)
                        .body(new RateLimitResponse("No repositories found or rate limit reached"));
            }

            // Process only the first repository for speed
            RepositoryInfo firstRepo = repos.get(0);
            String[] parts = firstRepo.getFullName().split("/");
            String owner = parts[0], name = parts[1];

            List<CommitActivity> commits = connectorService.fetchCommits(owner, name);
            RepoActivity activity = new RepoActivity(firstRepo.getName(), commits);

            List<RepoActivity> quickData = List.of(activity);
            ActivityResponse body = buildResponse(quickData);

            LOG.debug("Quick response: repo={}, commits={}",
                    firstRepo.getName(), commits.size());

            return ResponseEntity.ok(body);
        } catch (Exception ex) {
            LOG.error("Error while fetching quick GitHub activity for '{}'", userOrOrg, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Basic validation for GitHub usernames.
     */
    private boolean isValidGitHubIdentifier(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$");
        return pattern.matcher(candidate).matches();
    }

    /* ---------- helpers ---------- */

    private ActivityResponse buildResponse(List<RepoActivity> data) {
        return new ActivityResponse(buildMeta(data), data);
    }

    private Meta buildMeta(List<RepoActivity> data) {
        long totalCommits = data.stream()
                .flatMap(repo -> repo.getCommits().stream())
                .count();
        return new Meta(
                data.size(),
                totalCommits,
                java.time.ZonedDateTime.now().toString()
        );
    }
}
