package com.hyeokjin.devflow.taskservice.application;

import java.time.Instant;
import java.util.UUID;

public record TaskRecord(
        UUID taskId,
        String repository,
        String prompt,
        String status,
        String branch,
        int pullRequestNumber,
        String pullRequestUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
