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
    private final String topic;

    public TaskService(
            KafkaTemplate<String, DevTaskCreatedEvent> kafkaTemplate,
            @Value("${devflow.kafka.topics.dev-task-created}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CreateTaskResponse create(CreateTaskRequest request) {
        UUID taskId = UUID.randomUUID();

        DevTaskCreatedEvent event = new DevTaskCreatedEvent(
                "DEV_TASK_CREATED",
                taskId,
                request.repository(),
                request.prompt(),
                Instant.now()
        );

        kafkaTemplate.send(topic, taskId.toString(), event);

        return new CreateTaskResponse(taskId, "QUEUED");
    }
}
