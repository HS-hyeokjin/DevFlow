package com.hyeokjin.devflow.taskservice.application;

import com.hyeokjin.devflow.taskservice.api.TaskArtifactsResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaskArtifactService {

    private static final int MAX_TEXT_CHARACTERS = 120_000;

    private final Path workspaceRoot;

    public TaskArtifactService(
            @Value("${devflow.agent.workspace-root:./workspaces}")
            String workspaceRoot
    ) {
        this.workspaceRoot = Path.of(workspaceRoot)
                .toAbsolutePath()
                .normalize();
    }

    public TaskArtifactsResponse read(UUID taskId) {
        Path workspace = workspaceRoot
                .resolve(taskId.toString())
                .normalize();

        if (!workspace.startsWith(workspaceRoot)
                || Files.isSymbolicLink(workspace)
                || !Files.isDirectory(workspace)) {
            return empty(taskId);
        }

        Path devflow = workspace.resolve(".devflow").normalize();
        if (!devflow.startsWith(workspace)
                || Files.isSymbolicLink(devflow)
                || !Files.isDirectory(devflow)) {
            return empty(taskId);
        }

        return new TaskArtifactsResponse(
                taskId,
                true,
                readLines(devflow.resolve("changed-files.txt")),
                readText(devflow.resolve("analysis.md")),
                readText(devflow.resolve("change-plan.md")),
                readText(devflow.resolve("changes.diff")),
                readText(devflow.resolve("test-result.md")),
                readText(devflow.resolve("review-result.md")),
                readText(devflow.resolve("retry-history.md"))
        );
    }

    private TaskArtifactsResponse empty(UUID taskId) {
        return new TaskArtifactsResponse(
                taskId,
                false,
                List.of(),
                "",
                "",
                "",
                "",
                "",
                ""
        );
    }

    private List<String> readLines(Path file) {
        if (!safeRegularFile(file)) {
            return List.of();
        }

        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8)
                    .stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .distinct()
                    .limit(200)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read DevFlow artifact: " + file,
                    e
            );
        }
    }

    private String readText(Path file) {
        if (!safeRegularFile(file)) {
            return "";
        }

        try {
            String content = Files.readString(
                    file,
                    StandardCharsets.UTF_8
            );

            if (content.length() <= MAX_TEXT_CHARACTERS) {
                return content;
            }

            return content.substring(0, MAX_TEXT_CHARACTERS)
                    + "\n\n[TRUNCATED BY DEVFLOW DASHBOARD]";
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read DevFlow artifact: " + file,
                    e
            );
        }
    }

    private boolean safeRegularFile(Path file) {
        Path normalized = file.toAbsolutePath().normalize();

        if (!normalized.startsWith(workspaceRoot)
                || Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized)) {
            return false;
        }

        Path current = workspaceRoot;
        for (Path segment : workspaceRoot.relativize(normalized)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                return false;
            }
        }

        return true;
    }
}
