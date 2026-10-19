package com.hyeokjin.devflow.taskservice.application;

import com.hyeokjin.devflow.taskservice.api.ApprovalRequest;
import com.hyeokjin.devflow.taskservice.event.HumanDecisionEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HumanApprovalService {

    private final TaskStateStore taskStateStore;
    private final KafkaTemplate<String, HumanDecisionEvent> kafkaTemplate;
    private final String topic;

    public HumanApprovalService(
            TaskStateStore taskStateStore,
            KafkaTemplate<String, HumanDecisionEvent> kafkaTemplate,
            @Value("${devflow.kafka.topics.human-decision}") String topic
    ) {
        this.taskStateStore = taskStateStore;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public synchronized TaskRecord decide(
            UUID taskId,
            boolean approved,
            ApprovalRequest request
    ) {
        TaskRecord current = taskStateStore.find(taskId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Task not found: " + taskId
                ));

        if (!"WAITING_APPROVAL".equals(current.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Task is not waiting for approval. status="
                            + current.status()
            );
        }

        Instant now = Instant.now();
        HumanDecisionEvent event = new HumanDecisionEvent(
                approved ? "HUMAN_APPROVED" : "HUMAN_REJECTED",
                current.taskId(),
                current.repository(),
                current.branch(),
                current.pullRequestNumber(),
                current.pullRequestUrl(),
                approved,
                request.decidedBy(),
                request.reason() == null ? "" : request.reason(),
                now
        );

        kafkaTemplate.send(topic, taskId.toString(), event).join();

        return taskStateStore.update(
                taskId,
                approved ? "APPROVAL_SUBMITTED" : "REJECTION_SUBMITTED",
                null,
                null,
                null,
                now
        );
    }
}
