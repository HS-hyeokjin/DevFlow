package com.hyeokjin.devflow.codeagent.modification;

import com.hyeokjin.devflow.codeagent.analysis.ProjectFileSnapshot;
import com.hyeokjin.devflow.codeagent.analysis.ProjectScanner;
import com.hyeokjin.devflow.codeagent.analysis.ProjectSnapshot;
import com.hyeokjin.devflow.codeagent.git.LocalGitBranchService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CodeModificationService {

    private final ProjectScanner projectScanner;
    private final CodeModificationClient modificationClient;
    private final LocalGitBranchService localGitBranchService;

    public CodeModificationService(
            ProjectScanner projectScanner,
            CodeModificationClient modificationClient,
            LocalGitBranchService localGitBranchService
    ) {
        this.projectScanner = projectScanner;
        this.modificationClient = modificationClient;
        this.localGitBranchService = localGitBranchService;
    }

    public CodeModificationResult modify(
            UUID taskId,
            String repository,
            String userRequest,
            Path workspace,
            List<String> selectedFiles
    ) {
        String branchName = localGitBranchService.prepareTaskBranch(
                workspace,
                taskId
        );

        ProjectSnapshot snapshot = projectScanner.scanSelected(
                workspace,
                selectedFiles
        );

        CodeModificationProposal proposal = modificationClient.propose(
                repository,
                userRequest,
                snapshot.files()
        );

        List<String> changedFiles = applyChanges(
                workspace,
                snapshot.files(),
                proposal.changes()
        );

        saveChangedFilesManifest(workspace, changedFiles);

        String diff = localGitBranchService.createWorkingTreeDiff(workspace);

        Path planFile = savePlan(
                taskId,
                workspace,
                repository,
                branchName,
                proposal,
                changedFiles
        );

        Path diffFile = saveDiff(workspace, diff);

        return new CodeModificationResult(
                branchName,
                proposal.provider(),
                proposal.model(),
                proposal.mock(),
                proposal.inputTokens(),
                proposal.outputTokens(),
                changedFiles,
                planFile,
                diffFile,
                diff
        );
    }

    private List<String> applyChanges(
            Path workspace,
            List<ProjectFileSnapshot> selectedFiles,
            List<ProposedFileChange> changes
    ) {
        java.util.Map<String, ProjectFileSnapshot> allowedFiles = selectedFiles.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProjectFileSnapshot::path,
                        file -> file,
                        (first, ignored) -> first
                ));

        // Validate the entire AI response before modifying any files.
        // ProjectScanner uses shortened snapshots; writing those back would
        // otherwise silently destroy the remainder of a large source file.
        java.util.Map<String, Path> validatedTargets = new java.util.LinkedHashMap<>();
        for (ProposedFileChange change : changes) {
            ProjectFileSnapshot selected = allowedFiles.get(change.path());
            if (selected == null) {
                throw new IllegalArgumentException(
                        "AI attempted to modify a non-selected file: " + change.path()
                );
            }

            if (selected.content().contains("[TRUNCATED BY DEVFLOW]")) {
                throw new IllegalStateException(
                        "Refusing to overwrite a truncated source snapshot: " + change.path()
                );
            }

            if (change.content() == null) {
                throw new IllegalArgumentException("AI returned null file content");
            }

            Path target = workspace.resolve(change.path()).normalize();
            if (!target.startsWith(workspace.normalize())) {
                throw new IllegalArgumentException(
                        "AI attempted path traversal: " + change.path()
                );
            }

            Path component = workspace;
            for (Path segment : workspace.relativize(target)) {
                component = component.resolve(segment);
                if (Files.isSymbolicLink(component)) {
                    throw new IllegalArgumentException(
                            "AI attempted to modify a symbolic link: " + change.path()
                    );
                }
            }

            if (!Files.isRegularFile(target)) {
                throw new IllegalArgumentException(
                        "AI attempted to modify a missing file: " + change.path()
                );
            }

            if (validatedTargets.putIfAbsent(change.path(), target) != null) {
                throw new IllegalArgumentException(
                        "AI proposed duplicate changes for: " + change.path()
                );
            }
        }

        if (validatedTargets.isEmpty()) {
            throw new IllegalStateException("AI proposed no file changes");
        }

        for (ProposedFileChange change : changes) {
            try {
                Files.writeString(
                        validatedTargets.get(change.path()),
                        change.content(),
                        StandardCharsets.UTF_8
                );
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to write modified file: " + change.path(),
                        e
                );
            }
        }

        return List.copyOf(validatedTargets.keySet());
    }

    private void saveChangedFilesManifest(
            Path workspace,
            List<String> changedFiles
    ) {
        Path directory = workspace.resolve(".devflow");
        Path outputFile = directory.resolve("changed-files.txt");

        try {
            Files.createDirectories(directory);

            Set<String> allChangedFiles = new LinkedHashSet<>();
            if (Files.isRegularFile(outputFile)) {
                Files.readAllLines(outputFile, StandardCharsets.UTF_8).stream()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .forEach(allChangedFiles::add);
            }

            allChangedFiles.addAll(changedFiles);
            Files.write(outputFile, allChangedFiles, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save changed-file manifest", e);
        }
    }

    private Path savePlan(
            UUID taskId,
            Path workspace,
            String repository,
            String branchName,
            CodeModificationProposal proposal,
            List<String> changedFiles
    ) {
        try {
            Path directory = workspace.resolve(".devflow");
            Files.createDirectories(directory);

            Path outputFile = directory.resolve("change-plan.md");

            StringBuilder details = new StringBuilder();
            for (ProposedFileChange change : proposal.changes()) {
                details.append("- `")
                        .append(change.path())
                        .append("`: ")
                        .append(change.reason())
                        .append("\n");
            }

            String content = """
                    # DevFlow Change Plan

                    - Task ID: %s
                    - Repository: %s
                    - Local branch: %s
                    - Provider: %s
                    - Model: %s
                    - Mock: %s
                    - Estimated input tokens: %d
                    - Estimated output tokens: %d
                    - Changed files: %d

                    ## Summary

                    %s

                    ## File Changes

                    %s
                    """.formatted(
                    taskId,
                    repository,
                    branchName,
                    proposal.provider(),
                    proposal.model(),
                    proposal.mock(),
                    proposal.inputTokens(),
                    proposal.outputTokens(),
                    changedFiles.size(),
                    proposal.summary(),
                    details
            );

            Files.writeString(outputFile, content, StandardCharsets.UTF_8);
            return outputFile.toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save change plan", e);
        }
    }

    private Path saveDiff(Path workspace, String diff) {
        try {
            Path directory = workspace.resolve(".devflow");
            Files.createDirectories(directory);

            Path outputFile = directory.resolve("changes.diff");
            Files.writeString(outputFile, diff, StandardCharsets.UTF_8);
            return outputFile.toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save git diff", e);
        }
    }
}
