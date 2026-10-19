package com.hyeokjin.devflow.taskservice.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CodeModifiedEvent(
        String eventType,
        UUID taskId,
        String repository,
        String prompt,
        String workspace,
        String branch,
        List<String> changedFiles,
        Instant createdAt
) {
}
