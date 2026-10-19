package com.hyeokjin.devflow.reviewagent.service;

import com.hyeokjin.devflow.reviewagent.event.ReviewResultEventPublisher;
import com.hyeokjin.devflow.reviewagent.event.TestResultEvent;
import com.hyeokjin.devflow.reviewagent.github.GitHubPublishResult;
import com.hyeokjin.devflow.reviewagent.github.GitHubPublishService;
import com.hyeokjin.devflow.reviewagent.review.ReviewClient;
import com.hyeokjin.devflow.reviewagent.review.ReviewDecision;
import com.hyeokjin.devflow.reviewagent.review.ReviewRequest;
import com.hyeokjin.devflow.reviewagent.state.ReviewState;
import com.hyeokjin.devflow.reviewagent.state.ReviewStateStore;
import com.hyeokjin.devflow.reviewagent.workspace.ReviewWorkspace;
import com.hyeokjin.devflow.reviewagent.workspace.ReviewWorkspaceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReviewPipelineService {

    private static final Logger log =
            LoggerFactory.getLogger(ReviewPipelineService.class);

    private final ReviewWorkspaceService workspaceService;
    private final ReviewClient reviewClient;
    private final ReviewStateStore stateStore;
    private final GitHubPublishService publishService;
    private final ReviewResultEventPublisher eventPublisher;
    private final boolean publishEnabled;
    private final String githubToken;
    private final String baseBranch;

    public ReviewPipelineService(
            ReviewWorkspaceService workspaceService,
            ReviewClient reviewClient,
            ReviewStateStore stateStore,
            GitHubPublishService publishService,
            ReviewResultEventPublisher eventPublisher,
            @Value("${devflow.github.publish-enabled:false}") boolean publishEnabled,
            @Value("${DEVFLOW_GITHUB_TOKEN:}") String githubToken,
            @Value("${devflow.github.base-branch:main}") String baseBranch
    ) {
        this.workspaceService = workspaceService;
        this.reviewClient = reviewClient;
        this.stateStore = stateStore;
        this.publishService = publishService;
        this.eventPublisher = eventPublisher;
        this.publishEnabled = publishEnabled;
        this.githubToken = githubToken == null
                ? ""
                : githubToken.replaceAll("\\s+", "");
        this.baseBranch = baseBranch;
    }

    public String process(TestResultEvent event) {
        if (!"TEST_PASSED".equals(event.status())) {
            return "IGNORED";
        }

        ReviewWorkspace reviewWorkspace = workspaceService.load(event);
        ReviewState existing = stateStore.read(reviewWorkspace.workspace());

        if ("PR_CREATED".equals(existing.status())) {
            log.info(
                    "[REVIEW DUPLICATE SKIPPED] taskId={}, pr={}",
                    event.taskId(),
                    existing.pullRequestUrl()
            );
            return "PR_ALREADY_CREATED";
        }

        ReviewDecision decision = reviewClient.review(new ReviewRequest(
                event.repository(),
                event.prompt(),
                reviewWorkspace.changedFiles(),
                reviewWorkspace.diff(),
                event.command(),
                event.exitCode()
        ));

        Path reviewFile = saveReview(
                reviewWorkspace.workspace(),
                event,
                decision
        );

        if (!decision.approved()) {
            stateStore.save(
                    reviewWorkspace.workspace(),
                    new ReviewState("REJECTED", "", 0, "")
            );
            eventPublisher.publish(
                    event.taskId(),
                    event.repository(),
                    event.branch(),
                    false,
                    decision.summary(),
                    0,
                    ""
            );
            log.warn(
                    "[REVIEW REJECTED] taskId={}, resultFile={}",
                    event.taskId(),
                    reviewFile
            );
            return "REJECTED";
        }

        if (!publishEnabled) {
            stateStore.save(
                    reviewWorkspace.workspace(),
                    new ReviewState("APPROVED_LOCAL_ONLY", "", 0, "")
            );
            eventPublisher.publish(
                    event.taskId(),
                    event.repository(),
                    event.branch(),
                    true,
                    decision.summary(),
                    ""
            );
            log.info(
                    "[REVIEW APPROVED - PUBLISH DISABLED] taskId={}, resultFile={}",
                    event.taskId(),
                    reviewFile
            );
            return "APPROVED_LOCAL_ONLY";
        }

        if (githubToken.isBlank()) {
            stateStore.save(
                    reviewWorkspace.workspace(),
                    new ReviewState("APPROVED_TOKEN_MISSING", "", 0, "")
            );
            eventPublisher.publish(
                    event.taskId(),
                    event.repository(),
                    event.branch(),
                    true,
                    decision.summary() + " / GitHub token missing",
                    0,
                    ""
            );
            log.warn(
                    "[REVIEW APPROVED - GITHUB TOKEN MISSING] taskId={}",
                    event.taskId()
            );
            return "APPROVED_TOKEN_MISSING";
        }

        GitHubPublishResult published = publishService.publish(
                event.taskId(),
                reviewWorkspace.workspace(),
                event.repository(),
                event.branch(),
                baseBranch,
                event.prompt(),
                reviewWorkspace.changedFiles(),
                decision,
                githubToken
        );

        stateStore.save(
                reviewWorkspace.workspace(),
                new ReviewState(
                        "PR_CREATED",
                        published.commitSha(),
                        published.pullRequestNumber(),
                        published.pullRequestUrl()
                )
        );

        eventPublisher.publish(
                event.taskId(),
                event.repository(),
                event.branch(),
                true,
                decision.summary(),
                published.pullRequestNumber(),
                published.pullRequestUrl()
        );

        log.info(
                "[PULL REQUEST CREATED] taskId={}, prNumber={}, url={}",
                event.taskId(),
                published.pullRequestNumber(),
                published.pullRequestUrl()
        );

        return "PR_CREATED";
    }

    private Path saveReview(
            Path workspace,
            TestResultEvent event,
            ReviewDecision decision
    ) {
        Path file = workspace.resolve(".devflow/review-result.md");

        String findings = decision.findings().stream()
                .map(item -> "- " + item)
                .collect(java.util.stream.Collectors.joining("\n"));

        String content = """
                # DevFlow Review Result

                Task ID: %s
                Repository: %s
                Branch: %s
                Approved: %s
                Provider: %s
                Model: %s
                Mock: %s
                Estimated input tokens: %d
                Estimated output tokens: %d

                ## Summary

                %s

                ## Findings

                %s
                """.formatted(
                event.taskId(),
                event.repository(),
                event.branch(),
                decision.approved(),
                decision.provider(),
                decision.model(),
                decision.mock(),
                decision.inputTokens(),
                decision.outputTokens(),
                decision.summary(),
                findings
        );

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(
                    file,
                    content,
                    StandardCharsets.UTF_8
            );
            return file.toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to save review result",
                    e
            );
        }
    }
}
