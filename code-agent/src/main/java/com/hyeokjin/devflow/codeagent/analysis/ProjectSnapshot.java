package com.hyeokjin.devflow.codeagent.analysis;

import java.util.List;

public record ProjectSnapshot(
        List<ProjectFileSnapshot> files,
        int totalCharacters
) {
}
