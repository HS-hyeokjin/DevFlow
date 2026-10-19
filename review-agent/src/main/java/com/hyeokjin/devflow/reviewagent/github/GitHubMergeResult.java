package com.hyeokjin.devflow.reviewagent.github;

public record GitHubMergeResult(
        boolean merged,
        String sha,
        String message
) {
}
