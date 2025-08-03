package com.github_connector.github_connector.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Rate limit details for a GitHub API resource */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceLimits {
    
    /**
     * Max requests allowed
     */
    @Min(0)
    private int limit;
    
    /**
     * Requests remaining
     */
    @Min(0)
    private int remaining;
    
    /**
     * When limit resets (unix timestamp)
     */
    @NotNull
    private long reset;
    
    /**
     * Reset time (formatted)
     */
    @NotBlank
    private String resetTimeFormatted;
    
    /**
     * Requests used
     */
    @Min(0)
    private int used;
}
