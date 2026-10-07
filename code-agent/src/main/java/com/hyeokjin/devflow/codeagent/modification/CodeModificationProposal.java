package com.hyeokjin.devflow.codeagent.modification;

import java.util.List;

public record CodeModificationProposal(
        String provider,
        String model,
        boolean mock,
        String summary,
        int inputTokens,
        int outputTokens,
        List<ProposedFileChange> changes
) {
}
