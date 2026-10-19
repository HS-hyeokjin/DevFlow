package com.hyeokjin.devflow.reviewagent.git;

public record LocalCommitResult(
        String branch,
        String commitSha,
        boolean newCommit
) {
}
