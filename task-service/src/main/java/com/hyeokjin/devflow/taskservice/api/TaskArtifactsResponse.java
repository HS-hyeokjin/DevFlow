package com.hyeokjin.devflow.taskservice.api;

import java.util.List;
import java.util.UUID;

public record TaskArtifactsResponse(
        UUID taskId,
        boolean workspaceAvailable,
        List<String> changedFiles,
        String analysis,
        String changePlan,
        String diff,
        String testResult,
        String reviewResult,
        String retryHistory
) {
}
