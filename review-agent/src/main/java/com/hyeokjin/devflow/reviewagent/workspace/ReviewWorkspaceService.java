package com.hyeokjin.devflow.reviewagent.workspace;

import com.hyeokjin.devflow.reviewagent.event.TestResultEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReviewWorkspaceService {

    private final Path workspaceRoot;

    public ReviewWorkspaceService(
            @Value("${devflow.agent.workspace-root:./workspaces}") String workspaceRoot
    ) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    public ReviewWorkspace load(TestResultEvent event) {
        Path workspace = validateWorkspace(event);
        Path diffFile = workspace.resolve(".devflow/changes.diff");
        Path manifestFile = workspace.resolve(".devflow/changed-files.txt");

        if (!Files.isRegularFile(diffFile)) {
            throw new IllegalStateException("Missing DevFlow diff: " + diffFile);
        }
        if (!Files.isRegularFile(manifestFile)) {
            throw new IllegalStateException(
                    "Missing changed-file manifest: " + manifestFile
            );
        }

        try {
            String diff = Files.readString(diffFile, StandardCharsets.UTF_8);
            LinkedHashSet<String> unique = new LinkedHashSet<>();

            for (String line : Files.readAllLines(manifestFile, StandardCharsets.UTF_8)) {
                String path = line.trim();
                if (path.isBlank()) {
                    continue;
                }

                Path target = workspace.resolve(path).normalize();
                if (!target.startsWith(workspace)
                        || containsSymbolicLink(workspace, target)
                        || !Files.isRegularFile(target)) {
                    throw new IllegalArgumentException(
                            "Invalid changed-file manifest entry: " + path
                    );
                }

                unique.add(path.replace('\\', '/'));
            }

            if (unique.isEmpty()) {
                throw new IllegalStateException("Changed-file manifest is empty");
            }

            return new ReviewWorkspace(
                    workspace,
                    diff,
                    List.copyOf(unique)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read review workspace", e);
        }
    }

    private Path validateWorkspace(TestResultEvent event) {
        Objects.requireNonNull(event.taskId(), "Missing taskId");
        Objects.requireNonNull(event.repository(), "Missing repository");
        Objects.requireNonNull(event.branch(), "Missing branch");
        Objects.requireNonNull(event.workspace(), "Missing workspace");

        if (!event.repository().matches("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")) {
            throw new IllegalArgumentException(
                    "Invalid repository: " + event.repository()
            );
        }

        Path expected = workspaceRoot.resolve(event.taskId().toString()).normalize();
        Path supplied = Path.of(event.workspace()).toAbsolutePath().normalize();

        if (!supplied.equals(expected)
                || Files.isSymbolicLink(expected)
                || !Files.isDirectory(expected.resolve(".git"))) {
            throw new IllegalArgumentException(
                    "Unexpected review workspace: " + supplied
            );
        }

        if (!event.branch().equals("devflow/" + event.taskId())) {
            throw new IllegalArgumentException(
                    "Unexpected DevFlow branch: " + event.branch()
            );
        }

        return expected;
    }

    private boolean containsSymbolicLink(Path workspace, Path target) {
        Path current = workspace;
        for (Path segment : workspace.relativize(target)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }
}
