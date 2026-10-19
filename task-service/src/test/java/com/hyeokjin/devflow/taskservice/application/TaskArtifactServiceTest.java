package com.hyeokjin.devflow.taskservice.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskArtifactServiceTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void readsAvailableDevflowArtifacts() throws Exception {
        UUID taskId = UUID.randomUUID();
        Path devflow = workspaceRoot
                .resolve(taskId.toString())
                .resolve(".devflow");

        Files.createDirectories(devflow);
        Files.writeString(
                devflow.resolve("changed-files.txt"),
                "src/App.java\nsrc/App.java\n"
        );
        Files.writeString(
                devflow.resolve("changes.diff"),
                "diff --git a/src/App.java b/src/App.java"
        );

        TaskArtifactService service =
                new TaskArtifactService(workspaceRoot.toString());

        var result = service.read(taskId);

        assertTrue(result.workspaceAvailable());
        assertEquals(1, result.changedFiles().size());
        assertTrue(result.diff().contains("diff --git"));
    }
}
