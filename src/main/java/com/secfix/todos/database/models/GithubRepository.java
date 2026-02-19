package com.secfix.todos.database.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Date;
import lombok.Data;


@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class GithubRepository {
    private int id;

    @JsonProperty("stargazers_count")
    private int stars;

    @JsonProperty("open_issues_count")
    private int openIssuesCount;

    private GithubLicense license;

    private String url;

    @JsonProperty("created_at")
    private Date createdAt;

    @JsonProperty("updated_at")
    private Date updatedAt;
}
