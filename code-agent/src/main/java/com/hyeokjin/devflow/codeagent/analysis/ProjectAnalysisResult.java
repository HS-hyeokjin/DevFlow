package com.hyeokjin.devflow.codeagent.analysis;

import java.nio.file.Path;

public record ProjectAnalysisResult(
        String model,
        int filesAnalyzed,
        Path resultFile,
        String analysis
) {
}
