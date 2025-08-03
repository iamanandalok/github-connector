package com.github_connector.github_connector.model;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GitHub API rate limit status */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitInfo {
    
    /**
     * Core API limits
     */
    @Valid
    private ResourceLimits core;
    
    /**
     * Search API limits
     */
    @Valid
    private ResourceLimits search;
    
    /**
     * GraphQL API limits
     */
    @Valid
    private ResourceLimits graphql;
    
    /**
     * Integration manifest limits
     */
    @Valid
    private ResourceLimits integrationManifest;
    
    /**
     * Source import limits
     */
    @Valid
    private ResourceLimits sourceImport;
    
    /**
     * Code scanning limits
     */
    @Valid
    private ResourceLimits codeScanningUpload;
    
    /**
     * Actions runner limits
     */
    @Valid
    private ResourceLimits actionsRunnerRegistration;
    
    /**
     * SCIM API limits
     */
    @Valid
    private ResourceLimits scim;
    
    /**
     * Error message (if any)
     */
    private String message;
}
