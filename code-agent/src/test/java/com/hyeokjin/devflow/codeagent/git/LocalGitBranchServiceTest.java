package com.hyeokjin.devflow.codeagent.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalGitBranchServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsTaskBranchAndDiff() throws Exception {
        try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
            Files.writeString(tempDir.resolve("App.java"), "class App {}\n");
            git.add().addFilepattern("App.java").call();
            git.commit()
                    .setMessage("initial")
                    .setAuthor("DevFlow Test", "devflow@example.com")
                    .setCommitter("DevFlow Test", "devflow@example.com")
                    .call();
        }

        LocalGitBranchService service = new LocalGitBranchService();
        UUID taskId = UUID.randomUUID();

        String branch = service.prepareTaskBranch(tempDir, taskId);
        Files.writeString(
                tempDir.resolve("App.java"),
                "class App {}\n// changed\n"
        );

        String diff = service.createWorkingTreeDiff(tempDir);

        assertEquals("devflow/" + taskId, branch);
        assertTrue(diff.contains("+// changed"));
    }
}
