package com.secfix.todos.apis.dtos;

import com.secfix.todos.database.models.CodeRepository;
import com.secfix.todos.enums.CodeRepositoryStatus;
import lombok.Data;
import lombok.NoArgsConstructor;




@Data
@NoArgsConstructor
public class CodeRepositoryDto {
    private Integer id;
    private String name;
    private String owner;
    private String url;
    private Integer stars;
    private Integer openIssuesCount;
    private String license;
    private CodeRepositoryStatus status;

    public CodeRepositoryDto(CodeRepository repository) {
        this.setId(repository.getId());
        this.setName(repository.getName());
        this.setOwner(repository.getOwner());
        this.setUrl(repository.getUrl());
        this.setStars(repository.getStars());
        this.setOpenIssuesCount(repository.getOpenIssuesCount());
        this.setLicense(repository.getLicense());
        this.setStatus(repository.getStatus());
    }
}
