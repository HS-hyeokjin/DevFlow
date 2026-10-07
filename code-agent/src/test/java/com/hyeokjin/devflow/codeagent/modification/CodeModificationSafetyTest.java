package com.hyeokjin.devflow.codeagent.modification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hyeokjin.devflow.codeagent.analysis.ProjectFileSnapshot;
import com.hyeokjin.devflow.codeagent.analysis.ProjectScanner;
import com.hyeokjin.devflow.codeagent.analysis.ProjectSnapshot;
import com.hyeokjin.devflow.codeagent.git.LocalGitBranchService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeModificationSafetyTest {

    @TempDir
    Path workspace;

    @Test
    void rejectsTruncatedSnapshotRatherThanDestroyingFullSource() throws Exception {
        UUID taskId = UUID.randomUUID();
        Path target = workspace.resolve("src/Example.java");
        Files.createDirectories(target.getParent());
        String original = "class Example { /* long content */ }\n";
        Files.writeString(target, original);

        ProjectScanner scanner = mock(ProjectScanner.class);
        CodeModificationClient client = mock(CodeModificationClient.class);
        LocalGitBranchService branches = mock(LocalGitBranchService.class);

        ProjectFileSnapshot truncated = new ProjectFileSnapshot(
                "src/Example.java",
                "class Example {\n[TRUNCATED BY DEVFLOW]"
        );
        when(scanner.scanSelected(any(Path.class), any()))
                .thenReturn(new ProjectSnapshot(List.of(truncated), truncated.content().length()));
        when(branches.prepareTaskBranch(workspace, taskId))
                .thenReturn("devflow/" + taskId);
        when(client.propose(any(), any(), any()))
                .thenReturn(new CodeModificationProposal(
                        "mock", "mock", true, "test", 0, 0,
                        List.of(new ProposedFileChange(
                                "src/Example.java", "class Example {}", "modify"
                        ))
                ));

        CodeModificationService service =
                new CodeModificationService(scanner, client, branches);

        assertThrows(
                IllegalStateException.class,
                () -> service.modify(
                        taskId, "owner/repo", "modify", workspace,
                        List.of("src/Example.java")
                )
        );
        assertEquals(original, Files.readString(target));
    }
}
