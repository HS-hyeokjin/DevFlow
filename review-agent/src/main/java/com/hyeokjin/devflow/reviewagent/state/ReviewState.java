package com.hyeokjin.devflow.reviewagent.state;

public record ReviewState(
        String status,
        String commitSha,
        int pullRequestNumber,
        String pullRequestUrl
) {
    public static ReviewState initial() {
        return new ReviewState("PENDING", "", 0, "");
    }
}
