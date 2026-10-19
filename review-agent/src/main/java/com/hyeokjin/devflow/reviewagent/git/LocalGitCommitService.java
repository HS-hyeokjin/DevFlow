package com.hyeokjin.devflow.reviewagent.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.springframework.stereotype.Service;

@Service
public class LocalGitCommitService {

    public LocalCommitResult commit(
            Path workspace,
            String expectedBranch,
            List<String> changedFiles,
            String message
    ) {
        try (Git git = Git.open(workspace.toFile())) {
            String currentBranch = git.getRepository().getBranch();

            if (!expectedBranch.equals(currentBranch)) {
                throw new IllegalStateException(
                        "Workspace branch mismatch. expected="
                                + expectedBranch + ", actual=" + currentBranch
                );
            }

            for (String file : changedFiles) {
                git.add()
                        .setUpdate(true)
                        .addFilepattern(file)
                        .call();
            }

            Status status = git.status().call();
            boolean hasStagedChanges =
                    !status.getChanged().isEmpty()
                            || !status.getRemoved().isEmpty()
                            || !status.getAdded().isEmpty();

            if (hasStagedChanges) {
                ObjectId commit = git.commit()
                        .setMessage(message)
                        .setAuthor("DevFlow Agent", "devflow@localhost")
                        .setCommitter("DevFlow Agent", "devflow@localhost")
                        .call();

                return new LocalCommitResult(
                        currentBranch,
                        commit.name(),
                        true
                );
            }

            ObjectId head = git.getRepository().resolve("HEAD");
            if (head == null) {
                throw new IllegalStateException("Workspace has no HEAD commit");
            }

            return new LocalCommitResult(
                    currentBranch,
                    head.name(),
                    false
            );
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Failed to commit DevFlow changes", e);
        }
    }
}
