package com.secfix.todos.apis.dtos;

import com.secfix.todos.database.models.Task;
import com.secfix.todos.enums.TaskPriority;
import com.secfix.todos.enums.TaskStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TaskDto {
    private Integer id;
    private String name;
    private String description;
    private TaskPriority priority;
    private TaskStatus status;
    private UserDto owner;
    private CodeRepositoryDto codeRepository;

    public TaskDto(Task task) {
        this.setId(task.getId());
        this.setName(task.getName());
        this.setDescription(task.getDescription());
        this.setPriority(task.getPriority());
        this.setStatus(task.getStatus());
        this.setOwner(new UserDto(task.getOwner()));
        this.setCodeRepository(task.getCodeRepository() == null? null : new CodeRepositoryDto(task.getCodeRepository()));
    }
}
