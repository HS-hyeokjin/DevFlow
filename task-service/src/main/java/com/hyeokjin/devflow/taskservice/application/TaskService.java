package com.hyeokjin.devflow.taskservice.application;

import com.hyeokjin.devflow.taskservice.api.CreateTaskRequest;
import com.hyeokjin.devflow.taskservice.api.CreateTaskResponse;
import com.hyeokjin.devflow.taskservice.event.DevTaskCreatedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private final KafkaTemplate<String, DevTaskCreatedEvent> kafkaTemplate;
    private final TaskStateStore taskStateStore;
    private final String topic;

    public TaskService(
            KafkaTemplate<String, DevTaskCreatedEvent> kafkaTemplate,
            TaskStateStore taskStateStore,
            @Value("${devflow.kafka.topics.dev-task-created}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.taskStateStore = taskStateStore;
        this.topic = topic;
    }

    public CreateTaskResponse create(CreateTaskRequest request) {
        UUID taskId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        DevTaskCreatedEvent event = new DevTaskCreatedEvent(
                "DEV_TASK_CREATED",
                taskId,
                request.repository(),
                request.prompt(),
                createdAt
        );

        taskStateStore.save(new TaskRecord(
                taskId,
                request.repository(),
                request.prompt(),
                "QUEUED",
                "",
                0,
                "",
                createdAt,
                createdAt
        ));

        try {
            kafkaTemplate.send(topic, taskId.toString(), event).join();
        } catch (RuntimeException e) {
            taskStateStore.update(
                    taskId,
                    "FAILED_TO_QUEUE",
                    null,
                    null,
                    null,
                    Instant.now()
            );
            throw e;
        }

        return new CreateTaskResponse(taskId, "QUEUED");
    }
}
