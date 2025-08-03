package com.github_connector.github_connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RepositoryInfo {
    private String name;

    @JsonProperty("full_name")
    private String fullName;

    // getters & setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}
