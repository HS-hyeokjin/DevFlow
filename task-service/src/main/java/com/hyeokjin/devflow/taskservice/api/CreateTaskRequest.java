package com.hyeokjin.devflow.taskservice.api;

import jakarta.validation.constraints.NotBlank;

public record CreateTaskRequest(
        @NotBlank String repository,
        @NotBlank String prompt
) {
}
