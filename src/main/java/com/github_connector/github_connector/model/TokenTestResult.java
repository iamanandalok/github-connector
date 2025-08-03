package com.github_connector.github_connector.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of GitHub token validation test */
@Data
@NoArgsConstructor
public class TokenTestResult {
    
    /**
     * Whether token is valid
     */
    private boolean valid;
    
    /**
     * GitHub username (if valid)
     */
    private String username;
    
    /**
     * Validation message
     */
    @NotBlank
    private String message;
    
    /**
     * Error type (if invalid)
     */
    private String errorType;

    /**
     * Success case
     */
    public TokenTestResult(String username, String message) {
        this.valid = true;
        this.username = username;
        this.message = message;
        this.errorType = null;
    }
    
    /**
     * Error case
     */
    public TokenTestResult(boolean valid, String errorType, String message) {
        this.valid = valid;
        this.username = null;
        this.errorType = errorType;
        this.message = message;
    }
    
    /**
     * Check if token is valid
     */
    public boolean isValid() {
        return valid;
    }
}
