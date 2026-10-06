package com.hyeokjin.devflow.codeagent.analysis;

import java.nio.file.Path;

public record ProjectAnalysisResult(
        String provider,
        String model,
        boolean mock,
        int inputTokens,
        int outputTokens,
        int filesAnalyzed,
        Path resultFile,
        String analysis
) {
}
