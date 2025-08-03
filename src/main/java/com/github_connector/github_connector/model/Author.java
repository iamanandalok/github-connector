package com.github_connector.github_connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;

public class Author {
    private String name;
    private ZonedDateTime date;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ZonedDateTime getDate() { return date; }
    public void setDate(ZonedDateTime date) { this.date = date; }
}
