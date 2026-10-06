package com.hyeokjin.devflow.codeagent.analysis;

import java.nio.file.Path;
import java.util.List;

public record RepoMapResult(
        List<RepoMapEntry> entries,
        Path outputFile
) {
}
