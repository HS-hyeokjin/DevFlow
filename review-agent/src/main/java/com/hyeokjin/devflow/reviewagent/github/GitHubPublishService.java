package com.hyeokjin.devflow.reviewagent.github;

import com.hyeokjin.devflow.reviewagent.git.LocalCommitResult;
import com.hyeokjin.devflow.reviewagent.git.LocalGitCommitService;
import com.hyeokjin.devflow.reviewagent.review.ReviewDecision;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

@Service
public class GitHubPublishService {

    private final LocalGitCommitService commitService;
    private final GitHubPullRequestClient pullRequestClient;

    public GitHubPublishService(
            LocalGitCommitService commitService,
            GitHubPullRequestClient pullRequestClient
    ) {
        this.commitService = commitService;
        this.pullRequestClient = pullRequestClient;
    }

    public GitHubPublishResult publish(
            UUID taskId,
            Path workspace,
            String repository,
            String branch,
            String baseBranch,
            String userRequest,
            List<String> changedFiles,
            ReviewDecision review,
            String token
    ) {
        LocalCommitResult commit = commitService.commit(
                workspace,
                branch,
                changedFiles,
                "devflow: apply task " + taskId
        );

        push(workspace, branch, token);

        String title = "[DevFlow] " + compact(userRequest, 80);
        String body = """
                ## DevFlow automated pull request

                Task ID: %s
                Source branch: %s
                Tests: passed
                Review provider: %s
                Review model: %s

                ### Original request

                %s

                ### Changed files

                %s

                ### Review

                %s

                %s

                This PR was created automatically by DevFlow.
                Human review is still required before merge.
                """.formatted(
                taskId,
                branch,
                review.provider(),
                review.model(),
                userRequest,
                changedFiles.stream()
                        .map(path -> "- " + path)
                        .collect(java.util.stream.Collectors.joining("\n")),
                review.summary(),
                review.findings().stream()
                        .map(item -> "- " + item)
                        .collect(java.util.stream.Collectors.joining("\n"))
        );

        PullRequestResult pullRequest = pullRequestClient.findOrCreate(
                repository,
                branch,
                baseBranch,
                title,
                body,
                token
        );

        return new GitHubPublishResult(
                commit.commitSha(),
                pullRequest.number(),
                pullRequest.url()
        );
    }

    private void push(
            Path workspace,
            String branch,
            String token
    ) {
        try (Git git = Git.open(workspace.toFile())) {
            git.push()
                    .setRemote("origin")
                    .setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(
                                    "x-access-token",
                                    token
                            )
                    )
                    .setRefSpecs(new RefSpec(
                            "refs/heads/" + branch
                                    + ":refs/heads/" + branch
                    ))
                    .call();
        } catch (java.io.IOException | GitAPIException e) {
            throw new IllegalStateException(
                    "Failed to push DevFlow branch to GitHub: " + branch,
                    e
            );
        }
    }

    private String compact(
            String text,
            int maxLength
    ) {
        if (text == null || text.isBlank()) {
            return "Automated code change";
        }

        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= maxLength
                ? oneLine
                : oneLine.substring(0, maxLength);
    }
}
