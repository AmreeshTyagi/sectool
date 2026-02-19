package com.secfix.todos.apis.controllers;

import com.secfix.todos.apis.dtos.CodeRepositoryDto;
import com.secfix.todos.apis.dtos.requests.CodeRepositoryCreateRequest;
import com.secfix.todos.apis.dtos.requests.CodeRepositoryUpdateRequest;
import com.secfix.todos.exceptions.ApiServiceCallException;
import com.secfix.todos.services.CodeRepositoriesManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;




@CrossOrigin
@RestController
@RequestMapping("/code-repository")
public class CodeRepositoriesManagementController {
    private Logger logger = LoggerFactory.getLogger(CodeRepositoriesManagementController.class);

    @Autowired
    private CodeRepositoriesManagementService codeRepositoriesManagementService;

    @Operation(summary = "Get Code Repositories")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns Code Repositories list", content = {
                    @Content(array = @ArraySchema(schema = @Schema(implementation = CodeRepositoryDto.class)), mediaType = "application/json") }),
            @ApiResponse(responseCode = "500", description = "An error has occurred ", content = @Content)
    })
    @GetMapping("")
    public ResponseEntity<?> getCodeRepositories(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(this.codeRepositoriesManagementService.getCodeRepositories());
        } catch (ApiServiceCallException apiex) {
            return new ResponseEntity<>(apiex.getMessage(), apiex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error occured while getting code repositories", ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Get Code Repository by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns the Code Repository", content = {
                    @Content(schema = @Schema(implementation = CodeRepositoryDto.class), mediaType = "application/json") }),
            @ApiResponse(responseCode = "500", description = "An error has occurred ", content = @Content)
    })
    @GetMapping("/{codeRepositoryId}")
    public ResponseEntity<?> getCodeRepositoryById(HttpServletRequest request,
            @Parameter(description = "Code Repository id") @PathVariable Integer codeRepositoryId) {

        try {
            return ResponseEntity.ok(this.codeRepositoriesManagementService.getCodeRepositoryByIdInDto(codeRepositoryId));
        } catch (ApiServiceCallException apiex) {
            return new ResponseEntity<>(apiex.getMessage(), apiex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error occured while updating code repository <{}>", codeRepositoryId, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Create new Code Repository")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns the created Code Repository", content = {
                    @Content(schema = @Schema(implementation = CodeRepositoryDto.class), mediaType = "application/json") }),
            @ApiResponse(responseCode = "500", description = "An error has occurred ", content = @Content)
    })
    @PostMapping("")
    public ResponseEntity<?> createCodeRepository(HttpServletRequest request,
            @Parameter(description = "Create code repository request data") @Valid @RequestBody CodeRepositoryCreateRequest createRequest) {

        try {
            return ResponseEntity
                    .ok(this.codeRepositoriesManagementService.createodeRepository(createRequest));
        } catch (ApiServiceCallException apiex) {
            return new ResponseEntity<>(apiex.getMessage(), apiex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error occured while creating code repository",
                    ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Update Code Repository with id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns the updated Code Repository", content = {
                    @Content(schema = @Schema(implementation = CodeRepositoryDto.class), mediaType = "application/json") }),
            @ApiResponse(responseCode = "500", description = "An error has occurred ", content = @Content)
    })
    @PutMapping("/{codeRepositoryId}")
    public ResponseEntity<?> updateCodeRepository(HttpServletRequest request,
            @Parameter(description = "Code Repository update request data") @Valid @RequestBody CodeRepositoryUpdateRequest updateRequest,
            @Parameter(description = "Code Repository id") @PathVariable Integer codeRepositoryId) {

        try {
            return ResponseEntity
                    .ok(this.codeRepositoriesManagementService.updateCodeRepository(codeRepositoryId, updateRequest));
        } catch (ApiServiceCallException apiex) {
            return new ResponseEntity<>(apiex.getMessage(), apiex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error occured while updating code repository <{}>", codeRepositoryId, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Delete Code Repository with id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns nothing", content = {
                    @Content(mediaType = "application/json") }),
            @ApiResponse(responseCode = "500", description = "An error has occurred ", content = @Content)
    })
    @DeleteMapping("/{codeRepositoryId}")
    public ResponseEntity<?> deleteCodeRepository(HttpServletRequest request,
            @Parameter(description = "Code Repository id") @PathVariable Integer codeRepositoryId) {
        try {
            this.codeRepositoriesManagementService.deleteCodeRepository(codeRepositoryId);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (ApiServiceCallException apiex) {
            return new ResponseEntity<>(apiex.getMessage(), apiex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error occured while deleting code repository <{}>",
                    codeRepositoryId,
                    ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
