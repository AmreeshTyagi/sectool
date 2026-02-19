package com.secfix.todos.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secfix.todos.database.models.CodeRepository;
import com.secfix.todos.database.models.GithubRepository;
import com.secfix.todos.enums.CodeRepositoryStatus;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GithubServiceWrapper {
    Logger logger = LoggerFactory.getLogger(GithubServiceWrapper.class);
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CodeRepository getRepository(CodeRepository codeRepository) {
        logger.info("Get Github repository started, <Owner <{}>, Name <{}>>", codeRepository.getOwner(), codeRepository.getName());
        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest
                    .newBuilder()
                    .uri(URI.create(String.format("https://api.github.com/repos/%s/%s", codeRepository.getOwner(), codeRepository.getName())))
                    .header("Content-Type", "application/vnd.github+json")
                    .GET()
                    .build();

            java.net.http.HttpResponse<String> response = this.sendHttpRequest(request);

            if (List.of(403, 404).contains(response.statusCode())) {
                // repository not exist or we are not authorized to fetch
                this.logger.error("Get Github repository failed, <Owner <{}>, Name <{}>>, Status code <{}>",
                        codeRepository.getOwner(), codeRepository.getName(), response.statusCode());

                codeRepository.setStatus(CodeRepositoryStatus.INVALID);
                return codeRepository;
            }

            if (response.statusCode() != 200) {
                this.logger.error("Get Github repository failed, <Owner <{}>, Name <{}>>, Status code <{}>",
                        codeRepository.getOwner(), codeRepository.getName(), response.statusCode());
                return codeRepository;
            }

            GithubRepository githubRepository = objectMapper.readValue(
                                        response.body(),
                                        new TypeReference<GithubRepository>() {
                                        });
            
            codeRepository.setUrl(githubRepository.getUrl());
            codeRepository.setLicense(githubRepository.getLicense().getKey());
            codeRepository.setStars(githubRepository.getStars());
            codeRepository.setOpenIssuesCount(githubRepository.getOpenIssuesCount());
            codeRepository.setUpdatedAt(githubRepository.getUpdatedAt());
            codeRepository.setCreatedAt(githubRepository.getCreatedAt());
         
            logger.info("Get Github repository completed, <Owner <{}>, Name <{}>>", codeRepository.getOwner(), codeRepository.getName());

        } catch (Exception ex) {
            this.logger.error("Get Github repository failed, <Owner <{}>, Name <{}>>",
                    codeRepository.getOwner(), codeRepository.getName(),
                    ex);
        }

        return codeRepository;
    }
    
    private java.net.http.HttpResponse<String> sendHttpRequest(java.net.http.HttpRequest request) throws Exception {
        try {
            return httpClient.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            this.logger.error("Failed send request to Github api", ex);
            throw ex;
        }
    }
}
