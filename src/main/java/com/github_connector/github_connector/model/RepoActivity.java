package com.github_connector.github_connector.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Repository with its recent commits. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepoActivity {

    /** Repository name */
    @NotBlank
    private String repositoryName;

    /** Recent commits */
    @NotNull
    private List<CommitActivity> commits;
}
