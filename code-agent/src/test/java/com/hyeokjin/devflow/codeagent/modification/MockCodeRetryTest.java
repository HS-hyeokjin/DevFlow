package com.hyeokjin.devflow.codeagent.modification;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hyeokjin.devflow.codeagent.analysis.ProjectFileSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockCodeRetryTest {

    @Test
    void addsDistinctMarkersForEachRetryWithoutRealAi() {
        MockCodeModificationClient client = new MockCodeModificationClient(0);

        CodeModificationProposal initial = client.propose(
                "owner/repo", "Fix tests",
                List.of(new ProjectFileSnapshot("src/App.java", "class App {}\n"))
        );
        String first = initial.changes().getFirst().content();
        assertTrue(first.contains("DEVFLOW-MOCK-CHANGE"));

        CodeModificationProposal retry = client.propose(
                "owner/repo", "DEVFLOW_RETRY_ATTEMPT=1\nFix tests",
                List.of(new ProjectFileSnapshot("src/App.java", first))
        );
        String second = retry.changes().getFirst().content();
        assertTrue(second.contains("DEVFLOW-MOCK-RETRY-1"));

        CodeModificationProposal another = client.propose(
                "owner/repo", "DEVFLOW_RETRY_ATTEMPT=2\nFix tests",
                List.of(new ProjectFileSnapshot("src/App.java", second))
        );
        String third = another.changes().getFirst().content();
        assertTrue(third.contains("DEVFLOW-MOCK-RETRY-1"));
        assertTrue(third.contains("DEVFLOW-MOCK-RETRY-2"));
    }
}
