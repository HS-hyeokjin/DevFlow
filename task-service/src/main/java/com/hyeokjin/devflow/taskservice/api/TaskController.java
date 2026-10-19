package com.hyeokjin.devflow.taskservice.api;

import com.hyeokjin.devflow.taskservice.application.HumanApprovalService;
import com.hyeokjin.devflow.taskservice.application.TaskArtifactService;
import com.hyeokjin.devflow.taskservice.application.TaskService;
import com.hyeokjin.devflow.taskservice.application.TaskStateStore;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskStateStore taskStateStore;
    private final HumanApprovalService humanApprovalService;
    private final TaskArtifactService taskArtifactService;

    public TaskController(
            TaskService taskService,
            TaskStateStore taskStateStore,
            HumanApprovalService humanApprovalService,
            TaskArtifactService taskArtifactService
    ) {
        this.taskService = taskService;
        this.taskStateStore = taskStateStore;
        this.humanApprovalService = humanApprovalService;
        this.taskArtifactService = taskArtifactService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreateTaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(request);
    }

    @GetMapping
    public List<TaskView> list() {
        return taskStateStore.list().stream()
                .map(TaskView::from)
                .toList();
    }

    @GetMapping("/{taskId}")
    public TaskView get(@PathVariable UUID taskId) {
        return taskStateStore.find(taskId)
                .map(TaskView::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Task not found: " + taskId
                ));
    }

    @GetMapping("/{taskId}/artifacts")
    public TaskArtifactsResponse artifacts(@PathVariable UUID taskId) {
        taskStateStore.find(taskId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Task not found: " + taskId
                ));
        return taskArtifactService.read(taskId);
    }

    @PostMapping("/{taskId}/approve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskView approve(
            @PathVariable UUID taskId,
            @Valid @RequestBody ApprovalRequest request
    ) {
        return TaskView.from(
                humanApprovalService.decide(taskId, true, request)
        );
    }

    @PostMapping("/{taskId}/reject")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskView reject(
            @PathVariable UUID taskId,
            @Valid @RequestBody ApprovalRequest request
    ) {
        return TaskView.from(
                humanApprovalService.decide(taskId, false, request)
        );
    }
}
