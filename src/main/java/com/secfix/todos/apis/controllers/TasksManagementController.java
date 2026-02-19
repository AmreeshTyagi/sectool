package com.secfix.todos.apis.controllers;

import com.secfix.todos.apis.dtos.TaskDto;
import com.secfix.todos.apis.dtos.requests.TaskCreateRequest;
import com.secfix.todos.apis.dtos.requests.TaskUpdateRequest;
import com.secfix.todos.exceptions.ApiServiceCallException;
import com.secfix.todos.services.TasksManagementService;
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
@RequestMapping("/task")
public class TasksManagementController {
    private Logger logger = LoggerFactory.getLogger(TasksManagementController.class);

    @Autowired
    private TasksManagementService tasksManagementService;

    @Operation(summary = "Get Tasks")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns Tasks list", content = {
                    @Content(array = @ArraySchema(schema = @Schema(implementation = TaskDto.class)), mediaType = "application/json") }),
            @ApiResponse(responseCode = "500", description = "An error has occurred ", content = @Content)
    })
    @GetMapping("")
    public ResponseEntity<?> getTasks(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(this.tasksManagementService.getTasks());
        } catch (ApiServiceCallException apiex) {
            return new ResponseEntity<>(apiex.getMessage(), apiex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error occured while getting tasks", ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Get Task by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns the Task", content = {
                    @Content(schema = @Schema(implementation = TaskDto.class), mediaType = "application/json") }),
            @ApiResponse(responseCode = "500", description = "An error has occurred ", content = @Content)
    })
    @GetMapping("/{taskId}")
    public ResponseEntity<?> getTaskById(HttpServletRequest request,
            @Parameter(description = "Task id") @PathVariable Integer taskId) {

        try {
            return ResponseEntity.ok(this.tasksManagementService.getTaskByIdInDto(taskId));
        } catch (ApiServiceCallException apiex) {
            return new ResponseEntity<>(apiex.getMessage(), apiex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error occured while getting task <{}>", taskId, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Create new Task")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns the created Task", content = {
                    @Content(schema = @Schema(implementation = TaskDto.class), mediaType = "application/json") }),
            @ApiResponse(responseCode = "500", description = "An error has occurred ", content = @Content)
    })
    @PostMapping("")
    public ResponseEntity<?> createTask(HttpServletRequest request,
            @Parameter(description = "Create task request data") @Valid @RequestBody TaskCreateRequest createRequest) {

        try {
            return ResponseEntity
                    .ok(this.tasksManagementService.createTask(createRequest));
        } catch (ApiServiceCallException apiex) {
            return new ResponseEntity<>(apiex.getMessage(), apiex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error occured while creating task",
                    ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Update Task with id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns the updated Task", content = {
                    @Content(schema = @Schema(implementation = TaskDto.class), mediaType = "application/json") }),
            @ApiResponse(responseCode = "500", description = "An error has occurred ", content = @Content)
    })
    @PutMapping("/{taskId}")
    public ResponseEntity<?> updateTask(HttpServletRequest request,
            @Parameter(description = "Task update request data") @Valid @RequestBody TaskUpdateRequest updateRequest,
            @Parameter(description = "Task id") @PathVariable Integer taskId) {

        try {
            return ResponseEntity.ok(this.tasksManagementService.updateTask(taskId, updateRequest));
        } catch (ApiServiceCallException apiex) {
            return new ResponseEntity<>(apiex.getMessage(), apiex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error occured while updating task <{}>", taskId, ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Delete Task with id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns nothing", content = {
                    @Content(mediaType = "application/json") }),
            @ApiResponse(responseCode = "500", description = "An error has occurred ", content = @Content)
    })
    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteTask(HttpServletRequest request,
            @Parameter(description = "Task id") @PathVariable Integer taskId) {
        try {
            this.tasksManagementService.deleteTask(taskId);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (ApiServiceCallException apiex) {
            return new ResponseEntity<>(apiex.getMessage(), apiex.getHttpStatus());
        } catch (Exception ex) {
            logger.error("Error occured while deleting task <{}>",
                    taskId,
                    ex);
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
