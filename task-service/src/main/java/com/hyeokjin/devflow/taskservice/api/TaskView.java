package com.hyeokjin.devflow.taskservice.api;

import com.hyeokjin.devflow.taskservice.application.TaskRecord;
import java.time.Instant;
import java.util.UUID;

public record TaskView(
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
    public static TaskView from(TaskRecord record) {
        return new TaskView(
                record.taskId(),
                record.repository(),
                record.prompt(),
                record.status(),
                record.branch(),
                record.pullRequestNumber(),
                record.pullRequestUrl(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
