package com.hyeokjin.devflow.taskservice.event;

import java.time.Instant;
import java.util.UUID;

public record DevTaskCreatedEvent(
        String eventType,
        UUID taskId,
        String repository,
        String prompt,
        Instant createdAt
) {
}
