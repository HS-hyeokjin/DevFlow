package com.hyeokjin.devflow.reviewagent.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hyeokjin.devflow.reviewagent.event.TestResultEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewWorkspaceServiceTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void loadsDiffAndDeduplicatesManifest() throws Exception {
        UUID taskId = UUID.randomUUID();
        Path workspace = workspaceRoot.resolve(taskId.toString());

        Files.createDirectories(workspace.resolve(".git"));
        Files.createDirectories(workspace.resolve(".devflow"));
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(
                workspace.resolve("src/App.java"),
                "class App {}\n"
        );
        Files.writeString(
                workspace.resolve(".devflow/changes.diff"),
                "diff --git a/src/App.java b/src/App.java"
        );
        Files.writeString(
                workspace.resolve(".devflow/changed-files.txt"),
                "src/App.java\nsrc/App.java\n"
        );

        TestResultEvent event = new TestResultEvent(
                "TEST_PASSED",
                taskId,
                "owner/repo",
                "fix",
                workspace.toString(),
                "devflow/" + taskId,
                List.of("src/App.java"),
                "TEST_PASSED",
                "mvn verify",
                0,
                100,
                "BUILD SUCCESS",
                workspace.resolve(".devflow/test-result.md").toString(),
                Instant.now()
        );

        ReviewWorkspace loaded =
                new ReviewWorkspaceService(
                        workspaceRoot.toString()
                ).load(event);

        assertEquals(1, loaded.changedFiles().size());
        assertEquals("src/App.java", loaded.changedFiles().getFirst());
    }
}
