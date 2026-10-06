package com.hyeokjin.devflow.codeagent.analysis.ai;

public record AiAnalysisResponse(
        String provider,
        String model,
        String content,
        int inputTokens,
        int outputTokens,
        boolean mock
) {
}
