package com.secfix.todos.services;

import com.secfix.todos.apis.dtos.CodeRepositoryDto;
import com.secfix.todos.apis.dtos.requests.CodeRepositoryCreateRequest;
import com.secfix.todos.apis.dtos.requests.CodeRepositoryUpdateRequest;
import com.secfix.todos.database.models.CodeRepository;
import com.secfix.todos.database.repositories.CodeRepositoryRepository;
import com.secfix.todos.enums.CodeRepositoryStatus;
import com.secfix.todos.exceptions.ApiServiceCallException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;




@Service
public class CodeRepositoriesManagementService {
    private Logger logger = LoggerFactory.getLogger(CodeRepositoriesManagementService.class);

    @Autowired
    private CodeRepositoryRepository codeRepositoryRepository;

    public List<CodeRepositoryDto> getCodeRepositories() {
        return this.codeRepositoryRepository.findAll().stream()
                .map(r -> new CodeRepositoryDto(r))
                .collect(Collectors.toList());
    }

    public List<CodeRepository> getActiveCodeRepositories() {
        return this.codeRepositoryRepository.findByStatus(CodeRepositoryStatus.ACTIVE);
    }

    public void batchUpdateCodeRepositories(List<CodeRepository> codeRepositories) {
        try {
            this.codeRepositoryRepository.saveAll(codeRepositories);
        } catch (Exception ex) {
            logger.error("Batch update repositories failed", ex);
        }
    }

    public CodeRepository getCodeRepositoryById(Integer codeRepositoryId) {
        logger.debug("Get code repository started, CodeRepositoryId: <{}>", codeRepositoryId);
        try {
            Optional<CodeRepository> codeRepoRecord = this.codeRepositoryRepository.findById(codeRepositoryId);

            if (!codeRepoRecord.isPresent()) {
                throw new ApiServiceCallException(
                        String.format("Code Repository with id <%s> not exist", codeRepositoryId),
                        HttpStatus.BAD_REQUEST);
            }

            return codeRepoRecord.get();
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;

            logger.error("Get code repository failed, CodeRepositoryId: <{}>", codeRepositoryId, ex);
            throw new ApiServiceCallException(
                    "Update Code Repository failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public CodeRepositoryDto getCodeRepositoryByIdInDto(Integer codeRepositoryId) {
        return new CodeRepositoryDto(this.getCodeRepositoryById(codeRepositoryId));
    }

    public CodeRepositoryDto createodeRepository(CodeRepositoryCreateRequest request) {
        logger.info("Create code repository started, Data: <{}>", request.toString());
        try {
            CodeRepository codeRepo = this.codeRepositoryRepository.save(new CodeRepository(request));

            logger.info("Create code repository completed, Data: <{}>", request.toString());
            return new CodeRepositoryDto(codeRepo);
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;
            logger.error("Create code repository failed, Data: <{}>", request.toString(), ex);
            throw new ApiServiceCallException(
                    "Create code repository failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public CodeRepositoryDto updateCodeRepository(Integer codeRepositoryId, CodeRepositoryUpdateRequest request) {
        logger.info("Update code repository started, CodeRepositoryId: <{}>, Data: <{}>", codeRepositoryId,
                request.toString());
        try {
            CodeRepository codeRepo = this.getCodeRepositoryById(codeRepositoryId);

            if (StringUtils.isNotBlank(request.getName())) {
                codeRepo.setName(request.getName());
            }

            if (StringUtils.isNotBlank(request.getOwner())) {
                codeRepo.setOwner(request.getOwner());
            }

            if (request.getStatus() != null) {
                codeRepo.setStatus(request.getStatus());
            }

            codeRepo = this.codeRepositoryRepository.save(codeRepo);

            logger.info("Update code repository completed, CodeRepositoryId: <{}>, Data: <{}>", codeRepositoryId,
                    codeRepo.toString());
            return new CodeRepositoryDto(codeRepo);
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;

            logger.error("Update code repository failed, CodeRepositoryId: <{}>, Data: <{}>", codeRepositoryId,
                    request.toString(), ex);
            throw new ApiServiceCallException(
                    "Update code repository failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteCodeRepository(Integer codeRepositoryId) {
        logger.info("Delete code repository started, CodeRepositoryId: <{}>", codeRepositoryId);
        try {
            CodeRepository codeRepo = this.getCodeRepositoryById(codeRepositoryId);
            this.codeRepositoryRepository.delete(codeRepo);

            logger.info("Delete code repository completed, CodeRepositoryId: <{}>", codeRepositoryId);
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;
            logger.info("Delete code repository failed, CodeRepositoryId: <{}>", codeRepositoryId, ex);
            throw new ApiServiceCallException(
                    "Delete code repository failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
