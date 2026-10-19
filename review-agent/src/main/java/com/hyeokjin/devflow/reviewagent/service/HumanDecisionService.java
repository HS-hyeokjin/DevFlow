package com.hyeokjin.devflow.reviewagent.service;

import com.hyeokjin.devflow.reviewagent.event.HumanDecisionEvent;
import com.hyeokjin.devflow.reviewagent.event.MergeResultEventPublisher;
import com.hyeokjin.devflow.reviewagent.github.GitHubMergeResult;
import com.hyeokjin.devflow.reviewagent.github.GitHubPullRequestClient;
import com.hyeokjin.devflow.reviewagent.state.ReviewState;
import com.hyeokjin.devflow.reviewagent.state.ReviewStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HumanDecisionService {

    private final ReviewStateStore stateStore;
    private final GitHubPullRequestClient githubClient;
    private final MergeResultEventPublisher eventPublisher;
    private final Path workspaceRoot;
    private final boolean publishEnabled;
    private final String githubToken;

    public HumanDecisionService(
            ReviewStateStore stateStore,
            GitHubPullRequestClient githubClient,
            MergeResultEventPublisher eventPublisher,
            @Value("${devflow.agent.workspace-root:./workspaces}") String workspaceRoot,
            @Value("${devflow.github.publish-enabled:false}") boolean publishEnabled,
            @Value("${DEVFLOW_GITHUB_TOKEN:}") String githubToken
    ) {
        this.stateStore = stateStore;
        this.githubClient = githubClient;
        this.eventPublisher = eventPublisher;
        this.workspaceRoot = Path.of(workspaceRoot)
                .toAbsolutePath()
                .normalize();
        this.publishEnabled = publishEnabled;
        this.githubToken = githubToken == null
                ? ""
                : githubToken.replaceAll("\\s+", "");
    }

    public String handle(HumanDecisionEvent decision) {
        Path workspace = validate(decision);
        ReviewState state = stateStore.read(workspace);

        if ("MERGED".equals(state.status())) {
            return "ALREADY_MERGED";
        }
        if ("HUMAN_REJECTED".equals(state.status())) {
            return "ALREADY_REJECTED";
        }

        if (!"PR_CREATED".equals(state.status())
                || state.pullRequestNumber() != decision.pullRequestNumber()
                || !Objects.equals(
                        state.pullRequestUrl(),
                        decision.pullRequestUrl()
                )) {
            throw new IllegalStateException(
                    "Human decision does not match the saved PR state"
            );
        }

        if (!decision.approved()) {
            stateStore.save(
                    workspace,
                    new ReviewState(
                            "HUMAN_REJECTED",
                            state.commitSha(),
                            state.pullRequestNumber(),
                            state.pullRequestUrl()
                    )
            );
            eventPublisher.publish(
                    decision,
                    "HUMAN_REJECTED",
                    false,
                    decision.reason()
            );
            return "HUMAN_REJECTED";
        }

        if (!publishEnabled || githubToken.isBlank()) {
            eventPublisher.publish(
                    decision,
                    "MERGE_FAILED",
                    false,
                    "GitHub publish/merge is disabled or token is missing"
            );
            return "MERGE_FAILED";
        }

        GitHubMergeResult merge = githubClient.merge(
                decision.repository(),
                decision.pullRequestNumber(),
                githubToken
        );

        if (!merge.merged()) {
            eventPublisher.publish(
                    decision,
                    "MERGE_FAILED",
                    false,
                    merge.message()
            );
            return "MERGE_FAILED";
        }

        stateStore.save(
                workspace,
                new ReviewState(
                        "MERGED",
                        merge.sha(),
                        state.pullRequestNumber(),
                        state.pullRequestUrl()
                )
        );
        eventPublisher.publish(
                decision,
                "PULL_REQUEST_MERGED",
                true,
                merge.message()
        );
        return "PULL_REQUEST_MERGED";
    }

    private Path validate(HumanDecisionEvent decision) {
        Objects.requireNonNull(decision.taskId(), "Missing taskId");
        Objects.requireNonNull(decision.repository(), "Missing repository");
        Objects.requireNonNull(decision.branch(), "Missing branch");

        if (!decision.repository().matches(
                "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$"
        )) {
            throw new IllegalArgumentException(
                    "Invalid repository: " + decision.repository()
            );
        }

        if (!decision.branch().equals(
                "devflow/" + decision.taskId()
        )) {
            throw new IllegalArgumentException(
                    "Invalid task branch: " + decision.branch()
            );
        }

        if (decision.pullRequestNumber() <= 0) {
            throw new IllegalArgumentException(
                    "Human decision has no valid pull request"
            );
        }

        Path workspace = workspaceRoot
                .resolve(decision.taskId().toString())
                .normalize();

        if (!workspace.startsWith(workspaceRoot)
                || Files.isSymbolicLink(workspace)
                || !Files.isDirectory(workspace.resolve(".git"))) {
            throw new IllegalArgumentException(
                    "Invalid task workspace: " + workspace
            );
        }

        return workspace;
    }
}
