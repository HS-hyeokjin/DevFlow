package com.hyeokjin.devflow.codeagent.modification;

public record ProposedFileChange(
        String path,
        String content,
        String reason
) {
}
