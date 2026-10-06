package com.hyeokjin.devflow.codeagent.analysis;

public record RepoMapEntry(
        String path,
        String type,
        String symbols,
        int lineCount,
        long sizeBytes
) {
}
