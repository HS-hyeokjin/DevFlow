package com.hyeokjin.devflow.codeagent.modification;

import java.nio.file.Path;
import java.util.List;

public record CodeModificationResult(
        String branchName,
        String provider,
        String model,
        boolean mock,
        int inputTokens,
        int outputTokens,
        List<String> changedFiles,
        Path planFile,
        Path diffFile,
        String diff
) {
}
