package com.hyeokjin.devflow.reviewagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hyeokjin.devflow.reviewagent.event.HumanDecisionEvent;
import com.hyeokjin.devflow.reviewagent.event.MergeResultEventPublisher;
import com.hyeokjin.devflow.reviewagent.github.GitHubMergeResult;
import com.hyeokjin.devflow.reviewagent.github.GitHubPullRequestClient;
import com.hyeokjin.devflow.reviewagent.state.ReviewState;
import com.hyeokjin.devflow.reviewagent.state.ReviewStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HumanDecisionServiceTest {

    @TempDir
    Path workspaceRoot;

    private UUID taskId;
    private Path workspace;
    private ReviewStateStore stateStore;
    private GitHubPullRequestClient github;
    private MergeResultEventPublisher events;

    @BeforeEach
    void setUp() throws Exception {
        taskId = UUID.randomUUID();
        workspace = workspaceRoot.resolve(taskId.toString());
        Files.createDirectories(workspace.resolve(".git"));

        stateStore = new ReviewStateStore();
        stateStore.save(
                workspace,
                new ReviewState(
                        "PR_CREATED",
                        "commit",
                        42,
                        "https://github.com/owner/repo/pull/42"
                )
        );
        github = mock(GitHubPullRequestClient.class);
        events = mock(MergeResultEventPublisher.class);
    }

    @Test
    void mergesOnlyAfterHumanApproval() {
        HumanDecisionService service = new HumanDecisionService(
                stateStore,
                github,
                events,
                workspaceRoot.toString(),
                true,
                "token"
        );
        HumanDecisionEvent decision = decision(true);

        when(github.merge("owner/repo", 42, "token"))
                .thenReturn(new GitHubMergeResult(
                        true, "merged-sha", "merged"
                ));

        assertEquals("PULL_REQUEST_MERGED", service.handle(decision));
        assertEquals("MERGED", stateStore.read(workspace).status());
        verify(github).merge("owner/repo", 42, "token");
        verify(events).publish(
                decision,
                "PULL_REQUEST_MERGED",
                true,
                "merged"
        );
    }

    @Test
    void rejectionNeverCallsGitHubMerge() {
        HumanDecisionService service = new HumanDecisionService(
                stateStore,
                github,
                events,
                workspaceRoot.toString(),
                true,
                "token"
        );
        HumanDecisionEvent decision = decision(false);

        assertEquals("HUMAN_REJECTED", service.handle(decision));
        assertEquals(
                "HUMAN_REJECTED",
                stateStore.read(workspace).status()
        );
        verifyNoInteractions(github);
        verify(events).publish(
                decision,
                "HUMAN_REJECTED",
                false,
                "needs changes"
        );
    }

    private HumanDecisionEvent decision(boolean approved) {
        return new HumanDecisionEvent(
                approved ? "HUMAN_APPROVED" : "HUMAN_REJECTED",
                taskId,
                "owner/repo",
                "devflow/" + taskId,
                42,
                "https://github.com/owner/repo/pull/42",
                approved,
                "tester",
                approved ? "looks good" : "needs changes",
                Instant.now()
        );
    }
}
