package com.hyeokjin.devflow.reviewagent.review;

import java.util.List;

public record ReviewDecision(
        boolean approved,
        String provider,
        String model,
        boolean mock,
        String summary,
        List<String> findings,
        int inputTokens,
        int outputTokens
) {
}
