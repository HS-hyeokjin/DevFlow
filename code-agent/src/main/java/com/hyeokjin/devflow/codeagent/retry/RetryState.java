package com.hyeokjin.devflow.codeagent.retry;

public record RetryState(
        int attempts,
        String lastEventId,
        String status
) {
    public static RetryState initial() {
        return new RetryState(0, "", "PENDING");
    }
}
