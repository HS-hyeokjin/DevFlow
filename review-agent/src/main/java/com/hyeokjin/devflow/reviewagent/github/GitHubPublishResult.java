package com.hyeokjin.devflow.reviewagent.github;

public record GitHubPublishResult(
        String commitSha,
        int pullRequestNumber,
        String pullRequestUrl
) {
}
