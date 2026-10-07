package com.hyeokjin.devflow.codeagent.modification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hyeokjin.devflow.codeagent.analysis.ProjectFileSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockCodeModificationClientTest {

    @Test
    void proposesSafeSourceChangeWithoutExternalApi() {
        MockCodeModificationClient client = new MockCodeModificationClient(0);

        CodeModificationProposal proposal = client.propose(
                "owner/repo",
                "로그인 오류를 수정해줘",
                List.of(new ProjectFileSnapshot(
                        "src/main/java/example/AuthService.java",
                        "class AuthService {}"
                ))
        );

        assertEquals("mock", proposal.provider());
        assertTrue(proposal.mock());
        assertEquals(1, proposal.changes().size());
        assertTrue(proposal.changes().getFirst().content()
                .contains("DEVFLOW-MOCK-CHANGE"));
    }
}
