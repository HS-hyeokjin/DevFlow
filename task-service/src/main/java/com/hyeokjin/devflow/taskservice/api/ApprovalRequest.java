package com.hyeokjin.devflow.taskservice.api;

import jakarta.validation.constraints.NotBlank;

public record ApprovalRequest(
        @NotBlank String decidedBy,
        String reason
) {
}
