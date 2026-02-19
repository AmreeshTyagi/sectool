package com.secfix.todos.database.repositories;

import com.secfix.todos.database.models.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


public interface TaskRepository extends JpaRepository<Task, Integer> {

    List<Task> findByTenantId(UUID tenantId);
}
