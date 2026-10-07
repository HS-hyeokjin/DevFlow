package com.hyeokjin.devflow.reviewagent.workspace;

import java.nio.file.Path;
import java.util.List;

public record ReviewWorkspace(
        Path workspace,
        String diff,
        List<String> changedFiles
) {
}
