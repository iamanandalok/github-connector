package com.github_connector.github_connector.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response metadata with counts and timestamp.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Meta {
    
    /**
     * Number of repositories.
     */
    @Min(0)
    private long totalRepos;
    
    /**
     * Total commits across all repos.
     */
    @Min(0)
    private long totalCommits;
    
    /**
     * When data was fetched (ISO-8601).
     */
    @NotBlank
    private String fetchedAtIso;

}
