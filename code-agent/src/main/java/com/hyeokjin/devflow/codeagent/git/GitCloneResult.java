package com.hyeokjin.devflow.codeagent.git;

import java.nio.file.Path;

public record GitCloneResult(
        Path workspace,
        String branch,
        long fileCount,
        boolean reused
) {
}
