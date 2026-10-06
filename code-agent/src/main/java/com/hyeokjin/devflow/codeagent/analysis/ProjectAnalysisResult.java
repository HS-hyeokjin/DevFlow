package com.hyeokjin.devflow.codeagent.analysis;

import java.nio.file.Path;
import java.util.List;

public record ProjectAnalysisResult(
        String provider,
        String model,
        boolean mock,
        int inputTokens,
        int outputTokens,
        int filesAnalyzed,
        int repoMapFiles,
        Path repoMapFile,
        List<String> selectedFiles,
        Path resultFile,
        String analysis
) {
}
