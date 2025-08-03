package com.github_connector.github_connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CommitInfo {
    private String sha;

    @JsonProperty("commit")
    private CommitDetail commitDetail;  // renamed field to match your POJO

    public String getSha() { return sha; }
    public void setSha(String sha) { this.sha = sha; }

    public CommitDetail getCommitDetail() { return commitDetail; }
    public void setCommitDetail(CommitDetail commitDetail) {
        this.commitDetail = commitDetail;
    }
}
