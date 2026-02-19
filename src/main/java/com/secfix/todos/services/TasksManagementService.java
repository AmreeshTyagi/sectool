package com.secfix.todos.services;

import com.secfix.todos.apis.dtos.TaskDto;
import com.secfix.todos.apis.dtos.requests.TaskCreateRequest;
import com.secfix.todos.apis.dtos.requests.TaskUpdateRequest;
import com.secfix.todos.database.models.CodeRepository;
import com.secfix.todos.database.models.Task;
import com.secfix.todos.database.models.UserInfo;
import com.secfix.todos.database.repositories.TaskRepository;
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
public class TasksManagementService {
    private Logger logger = LoggerFactory.getLogger(TasksManagementService.class);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private CodeRepositoriesManagementService codeRepositoriesManagementService;

    @Autowired
    private UsersManagementService usersManagementService;

    public List<TaskDto> getTasks() {
        return this.taskRepository.findAll().stream()
                .map(u -> new TaskDto(u))
                .collect(Collectors.toList());
    }

    public Task getTaskById(Integer taskId) {
        logger.debug("Get task started, TaskId: <{}>", taskId);
        try {
            Optional<Task> taskRecord = this.taskRepository.findById(taskId);

            if (!taskRecord.isPresent()) {
                throw new ApiServiceCallException(
                        String.format("Task with id <%s> not exist", taskId),
                        HttpStatus.BAD_REQUEST);
            }

            return taskRecord.get();
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;

            logger.error("Get task failed, TaskId: <{}>", taskId, ex);
            throw new ApiServiceCallException(
                    "Update task failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public TaskDto getTaskByIdInDto(Integer taskId) {
        return new TaskDto(this.getTaskById(taskId));
    }

    public TaskDto createTask(TaskCreateRequest request) {
        logger.info("Create task started, Data: <{}>", request.toString());
        try {
            UserInfo owner = this.usersManagementService.getUserById(request.getOwner());
            CodeRepository codeRepository = null;

            if (request.getCodeRepository() != null) {
                codeRepository = this.codeRepositoriesManagementService
                        .getCodeRepositoryById(request.getCodeRepository());

                if (!codeRepository.getStatus().equals(CodeRepositoryStatus.ACTIVE)) {
                    throw new ApiServiceCallException(
                            "You can link tasks only to Active Code Repositories.",
                            HttpStatus.BAD_REQUEST);
                }
            }

            Task task = this.taskRepository.save(new Task(request, owner, codeRepository));

            logger.info("Create task completed, Data: <{}>", request.toString());
            return new TaskDto(task);
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;
            logger.error("Create task failed, Data: <{}>", request.toString(), ex);
            throw new ApiServiceCallException(
                    "Create task failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public TaskDto updateTask(Integer taskId, TaskUpdateRequest request) {
        logger.info("Update task started, TaskId: <{}>, Data: <{}>", taskId, request.toString());
        try {
            Task task = this.getTaskById(taskId);

            if (request.getOwner() != null) {
                task.setOwner(this.usersManagementService.getUserById(request.getOwner()));
            }

            if (request.getCodeRepository() != null) {
                CodeRepository codeRepository = this.codeRepositoriesManagementService
                        .getCodeRepositoryById(request.getCodeRepository());

                if (!codeRepository.getStatus().equals(CodeRepositoryStatus.ACTIVE)) {
                    throw new ApiServiceCallException(
                            "You can link tasks only to Active Code Repositories.",
                            HttpStatus.BAD_REQUEST);
                }

                task.setCodeRepository(codeRepository);
            }

            if (StringUtils.isNotBlank(request.getName())) {
                task.setName(request.getName());
            }

            if (StringUtils.isNotBlank(request.getDescription())) {
                task.setDescription(request.getDescription());
            }

            if (request.getPriority() != null) {
                task.setPriority(request.getPriority());
            }

            if (request.getStatus() != null) {
                task.setStatus(request.getStatus());
            }

            task = this.taskRepository.save(task);

            logger.info("Update task completed, TaskId: <{}>, Data: <{}>", taskId, task.toString());
            return new TaskDto(task);
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;

            logger.error("Update task failed, TaskId: <{}>, Data: <{}>", taskId, request.toString(), ex);
            throw new ApiServiceCallException(
                    "Update task failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteTask(Integer taskId) {
        logger.info("Delete task started, TaskId: <{}>", taskId);
        try {
            Task task = this.getTaskById(taskId);
            this.taskRepository.delete(task);

            logger.info("Delete task completed, TaskId: <{}>", taskId);
        } catch (Exception ex) {
            if (ex instanceof ApiServiceCallException)
                throw (ApiServiceCallException) ex;
            logger.info("Delete task failed, TaskId: <{}>", taskId, ex);
            throw new ApiServiceCallException(
                    "Delete task failed.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
