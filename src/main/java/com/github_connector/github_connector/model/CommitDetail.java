package com.github_connector.github_connector.model;

public class CommitDetail {
    private String message;
    private Author author;    // now refers to the top-level Author class

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Author getAuthor() { return author; }
    public void setAuthor(Author author) { this.author = author; }
}
