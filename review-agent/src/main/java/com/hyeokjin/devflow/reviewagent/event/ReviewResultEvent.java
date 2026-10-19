package com.hyeokjin.devflow.reviewagent.event;

import java.time.Instant;
import java.util.UUID;

public record ReviewResultEvent(
        String eventType,
        UUID taskId,
        String repository,
        String branch,
        boolean approved,
        String summary,
        String pullRequestUrl,
        Instant createdAt
) {
}
