package com.hyeokjin.devflow.reviewagent.github;

public record PullRequestResult(
        int number,
        String url,
        boolean created
) {
}
