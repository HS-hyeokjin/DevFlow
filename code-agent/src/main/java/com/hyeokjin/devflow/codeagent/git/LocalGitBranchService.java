package com.hyeokjin.devflow.codeagent.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Ref;
import org.springframework.stereotype.Service;

@Service
public class LocalGitBranchService {

    public String prepareTaskBranch(Path workspace, UUID taskId) {
        String branchName = "devflow/" + taskId;

        try (Git git = Git.open(workspace.toFile())) {
            Ref existing = git.getRepository()
                    .findRef("refs/heads/" + branchName);

            if (existing == null) {
                git.checkout()
                        .setCreateBranch(true)
                        .setName(branchName)
                        .call();
            } else {
                git.checkout()
                        .setName(branchName)
                        .call();
            }

            return branchName;
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException(
                    "Failed to prepare local task branch: " + branchName,
                    e
            );
        }
    }

    public String createWorkingTreeDiff(Path workspace) {
        try (Git git = Git.open(workspace.toFile());
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             DiffFormatter formatter = new DiffFormatter(output)) {

            List<DiffEntry> entries = git.diff().call();

            formatter.setRepository(git.getRepository());
            formatter.setDiffComparator(RawTextComparator.DEFAULT);
            formatter.setDetectRenames(true);

            for (DiffEntry entry : entries) {
                formatter.format(entry);
            }

            formatter.flush();
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException(
                    "Failed to create git diff for workspace: " + workspace,
                    e
            );
        }
    }
}
