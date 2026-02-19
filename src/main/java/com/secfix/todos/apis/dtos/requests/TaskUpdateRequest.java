package com.secfix.todos.apis.dtos.requests;

import com.secfix.todos.enums.TaskPriority;
import com.secfix.todos.enums.TaskStatus;
import lombok.Data;


@Data
public class TaskUpdateRequest {
    private String name;
    private String description;
    private TaskPriority priority;
    private TaskStatus status;
    private Integer owner;
    private Integer codeRepository;

    @Override
    public String toString() {
        return String.format("Name: %s, Priority: %s", this.getName() == null ? "" : this.getName(),
                this.getPriority() == null ? "" : this.getPriority().toString());
    }
}
