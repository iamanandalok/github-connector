package com.github_connector.github_connector.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Main response object for GitHub repository activity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityResponse {
    
    /**
     * Response metadata.
     */
    @NotNull
    private Meta meta;
    
    /**
     * Repository activities.
     */
    @NotNull
    private List<RepoActivity> data;
}
