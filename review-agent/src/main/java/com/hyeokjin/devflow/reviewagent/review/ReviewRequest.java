package com.hyeokjin.devflow.reviewagent.review;

import java.util.List;

public record ReviewRequest(
        String repository,
        String userRequest,
        List<String> changedFiles,
        String diff,
        String testCommand,
        int testExitCode
) {
}
