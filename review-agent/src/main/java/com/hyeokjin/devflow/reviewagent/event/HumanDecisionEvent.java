package com.hyeokjin.devflow.reviewagent.event;

import java.time.Instant;
import java.util.UUID;

public record HumanDecisionEvent(
        String eventType,
        UUID taskId,
        String repository,
        String branch,
        int pullRequestNumber,
        String pullRequestUrl,
        boolean approved,
        String decidedBy,
        String reason,
        Instant createdAt
) {
}
