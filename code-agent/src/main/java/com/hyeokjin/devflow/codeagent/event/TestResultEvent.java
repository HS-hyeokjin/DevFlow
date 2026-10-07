package com.hyeokjin.devflow.codeagent.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mirrors the JSON contract published by the independent Test Agent.
 */
public record TestResultEvent(
        String eventType,
        UUID taskId,
        String repository,
        String prompt,
        String workspace,
        String branch,
        List<String> changedFiles,
        String status,
        String command,
        int exitCode,
        long durationMs,
        String outputTail,
        String resultFile,
        Instant createdAt
) {
}
