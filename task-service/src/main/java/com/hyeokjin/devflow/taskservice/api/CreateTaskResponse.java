package com.hyeokjin.devflow.taskservice.api;

import java.util.UUID;

public record CreateTaskResponse(
        UUID taskId,
        String status
) {
}
