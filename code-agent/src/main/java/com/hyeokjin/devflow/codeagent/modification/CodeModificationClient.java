package com.hyeokjin.devflow.codeagent.modification;

import com.hyeokjin.devflow.codeagent.analysis.ProjectFileSnapshot;
import java.util.List;

public interface CodeModificationClient {

    CodeModificationProposal propose(
            String repository,
            String userRequest,
            List<ProjectFileSnapshot> files
    );
}
