package com.github_connector.github_connector.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Commit details for API responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitActivity {

    /** Commit message */
    @NotBlank
    private String message;

    /** Author name */
    @NotBlank
    private String author;

    /** When committed */
    @NotNull
    private ZonedDateTime timestamp;
}
