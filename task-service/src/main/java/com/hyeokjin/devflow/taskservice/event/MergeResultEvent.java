package com.hyeokjin.devflow.taskservice.event;

import java.time.Instant;
import java.util.UUID;

public record MergeResultEvent(
        String eventType,
        UUID taskId,
        String repository,
        String branch,
        int pullRequestNumber,
        String pullRequestUrl,
        boolean merged,
        String message,
        Instant createdAt
) {
}
