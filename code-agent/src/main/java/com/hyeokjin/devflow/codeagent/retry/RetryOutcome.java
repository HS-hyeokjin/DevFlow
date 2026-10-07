package com.hyeokjin.devflow.codeagent.retry;

public enum RetryOutcome {
    IGNORED,
    COMPLETED,
    RETRY_PUBLISHED,
    DUPLICATE,
    MAX_RETRIES_REACHED
}
