package com.hyeokjin.devflow.codeagent.analysis;

import java.util.List;

public record RelevantFileSelection(
        List<String> paths,
        List<ScoredFile> scores
) {
    public record ScoredFile(
            String path,
            int score
    ) {
    }
}
