package com.hyeokjin.devflow.reviewagent.git;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalGitCommitServiceTest {

    @TempDir
    Path workspace;

    @Test
    void commitsOnlyManifestTrackedFiles() throws Exception {
        try (Git git = Git.init().setDirectory(workspace.toFile()).call()) {
            Files.createDirectories(workspace.resolve("src"));
            Files.writeString(
                    workspace.resolve("src/App.java"),
                    "class App {}\n"
            );
            Files.writeString(
                    workspace.resolve("Other.txt"),
                    "unchanged\n"
            );

            git.add().addFilepattern("src/App.java").call();
            git.add().addFilepattern("Other.txt").call();
            git.commit()
                    .setMessage("initial")
                    .setAuthor("Test", "test@example.com")
                    .setCommitter("Test", "test@example.com")
                    .call();

            git.checkout()
                    .setCreateBranch(true)
                    .setName("devflow/task")
                    .call();
        }

        Files.writeString(
                workspace.resolve("src/App.java"),
                "class App {}\n// DevFlow change\n"
        );
        Files.writeString(
                workspace.resolve("Other.txt"),
                "local unrelated change\n"
        );
        Files.createDirectories(workspace.resolve(".devflow"));
        Files.writeString(
                workspace.resolve(".devflow/review-result.md"),
                "artifact"
        );

        LocalCommitResult result = new LocalGitCommitService().commit(
                workspace,
                "devflow/task",
                List.of("src/App.java"),
                "devflow test"
        );

        assertTrue(result.newCommit());

        try (Git git = Git.open(workspace.toFile())) {
            var status = git.status().call();
            assertTrue(status.getModified().contains("Other.txt"));
            assertTrue(
                    status.getUntracked().contains(".devflow/review-result.md")
            );
            assertFalse(status.getModified().contains("src/App.java"));
        }
    }
}
