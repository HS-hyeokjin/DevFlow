package com.hyeokjin.devflow.reviewagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hyeokjin.devflow.reviewagent.event.ReviewResultEventPublisher;
import com.hyeokjin.devflow.reviewagent.event.TestResultEvent;
import com.hyeokjin.devflow.reviewagent.github.GitHubPublishService;
import com.hyeokjin.devflow.reviewagent.review.MockReviewClient;
import com.hyeokjin.devflow.reviewagent.state.ReviewStateStore;
import com.hyeokjin.devflow.reviewagent.workspace.ReviewWorkspaceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewPipelineServiceTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void approvedReviewDoesNotTouchGitHubWhenPublishingDisabled()
            throws Exception {
        UUID taskId = UUID.randomUUID();
        Path workspace = workspaceRoot.resolve(taskId.toString());

        Files.createDirectories(workspace.resolve(".git"));
        Files.createDirectories(workspace.resolve(".devflow"));
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(
                workspace.resolve("src/App.java"),
                "class App {}\n"
        );
        Files.writeString(
                workspace.resolve(".devflow/changes.diff"),
                "diff --git a/src/App.java b/src/App.java\n+// change"
        );
        Files.writeString(
                workspace.resolve(".devflow/changed-files.txt"),
                "src/App.java\n"
        );

        GitHubPublishService github = mock(GitHubPublishService.class);
        ReviewResultEventPublisher events =
                mock(ReviewResultEventPublisher.class);
        ReviewStateStore stateStore = new ReviewStateStore();

        ReviewPipelineService service = new ReviewPipelineService(
                new ReviewWorkspaceService(workspaceRoot.toString()),
                new MockReviewClient(),
                stateStore,
                github,
                events,
                false,
                "",
                "main"
        );

        TestResultEvent event = new TestResultEvent(
                "TEST_PASSED",
                taskId,
                "owner/repo",
                "fix",
                workspace.toString(),
                "devflow/" + taskId,
                List.of("src/App.java"),
                "TEST_PASSED",
                "mvn verify",
                0,
                100,
                "BUILD SUCCESS",
                workspace.resolve(".devflow/test-result.md").toString(),
                Instant.now()
        );

        assertEquals("APPROVED_LOCAL_ONLY", service.process(event));
        assertEquals(
                "APPROVED_LOCAL_ONLY",
                stateStore.read(workspace).status()
        );
        verifyNoInteractions(github);
        verify(events).publish(
                taskId,
                "owner/repo",
                "devflow/" + taskId,
                true,
                "Mock review approved: 테스트 통과 및 변경 diff/manifest 확인",
                0,
                ""
        );
    }
}
