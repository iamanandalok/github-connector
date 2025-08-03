package com.github_connector.github_connector.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Error response for rate limit issues */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResponse {
    
    /**
     * Error message
     */
    @NotBlank
    private String message;
}
