package com.secfix.todos.apis.dtos.requests;

import com.secfix.todos.enums.TaskPriority;
import com.secfix.todos.enums.TaskStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;



@Data
public class TaskCreateRequest {
    @NotNull(message = "Name can't be null")
    @NotEmpty(message = "Name can't be empty")
    private String name;

    private String description;

    @NotNull(message = "Priority can't be null")
    private TaskPriority priority;

    @NotNull(message = "Status can't be null")
    private TaskStatus status;

    /**
     * The owner User.id
     */
    @NotNull(message = "Owner can't be null")
    private Integer owner;

    /**
     * The related CodeRepository.id
     */
    private Integer codeRepository;

    @Override
    public String toString() {
        return String.format("Name: %s, Priority: %s", this.getName(), this.getPriority().toString());
    }
}
