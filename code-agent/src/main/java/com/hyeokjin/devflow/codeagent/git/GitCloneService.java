package com.hyeokjin.devflow.codeagent.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GitCloneService {

    private static final Pattern GITHUB_REPOSITORY_PATTERN =
            Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");

    private final Path workspaceRoot;
    private final String githubToken;

    public GitCloneService(
            @Value("${devflow.agent.workspace-root:./workspaces}") String workspaceRoot,
            @Value("${devflow.agent.github-token:}") String githubToken
    ) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.githubToken = githubToken == null ? "" : githubToken.trim();
    }

    public GitCloneResult cloneRepository(UUID taskId, String repository) {
        validateRepository(repository);

        Path workspace = workspaceRoot.resolve(taskId.toString()).normalize();
        if (!workspace.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Invalid workspace path");
        }

        try {
            Files.createDirectories(workspaceRoot);

            if (Files.isDirectory(workspace.resolve(".git"))) {
                try (Git git = Git.open(workspace.toFile())) {
                    return new GitCloneResult(
                            workspace,
                            git.getRepository().getBranch(),
                            countWorkspaceFiles(workspace),
                            true
                    );
                }
            }

            if (Files.exists(workspace)) {
                deleteRecursively(workspace);
            }

            String cloneUrl = "https://github.com/" + repository + ".git";

            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(cloneUrl)
                    .setDirectory(workspace.toFile())
                    .setCloneAllBranches(false)
                    .setDepth(1);

            if (!githubToken.isBlank()) {
                cloneCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider("x-access-token", githubToken)
                );
            }

            try (Git git = cloneCommand.call()) {
                return new GitCloneResult(
                        workspace,
                        git.getRepository().getBranch(),
                        countWorkspaceFiles(workspace),
                        false
                );
            }
        } catch (GitAPIException e) {
            throw new IllegalStateException(
                    "Git clone failed for " + repository
                            + ". If this is a private repository, set the GITHUB_TOKEN environment variable.",
                    e
            );
        } catch (IOException e) {
            throw new IllegalStateException("Workspace operation failed: " + workspace, e);
        }
    }

    private void validateRepository(String repository) {
        if (repository == null || !GITHUB_REPOSITORY_PATTERN.matcher(repository).matches()) {
            throw new IllegalArgumentException(
                    "Repository must use the GitHub owner/repository format: " + repository
            );
        }
    }

    private long countWorkspaceFiles(Path workspace) throws IOException {
        try (Stream<Path> paths = Files.walk(workspace)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !workspace.relativize(path).startsWith(".git"))
                    .count();
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(current -> {
                        try {
                            Files.deleteIfExists(current);
                        } catch (IOException e) {
                            throw new WorkspaceDeleteException(e);
                        }
                    });
        } catch (WorkspaceDeleteException e) {
            throw e.getCause();
        }
    }

    private static final class WorkspaceDeleteException extends RuntimeException {

        private WorkspaceDeleteException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
