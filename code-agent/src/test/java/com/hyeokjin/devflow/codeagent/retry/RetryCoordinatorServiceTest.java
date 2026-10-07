package com.hyeokjin.devflow.codeagent.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hyeokjin.devflow.codeagent.analysis.ProjectAnalysisResult;
import com.hyeokjin.devflow.codeagent.analysis.ProjectAnalysisService;
import com.hyeokjin.devflow.codeagent.event.CodeModifiedEventPublisher;
import com.hyeokjin.devflow.codeagent.event.TestResultEvent;
import com.hyeokjin.devflow.codeagent.modification.CodeModificationResult;
import com.hyeokjin.devflow.codeagent.modification.CodeModificationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetryCoordinatorServiceTest {

    @TempDir
    Path workspaceRoot;

    private UUID taskId;
    private Path workspace;
    private ProjectAnalysisService analysis;
    private CodeModificationService modification;
    private CodeModifiedEventPublisher publisher;
    private RetryStateStore stateStore;
    private RetryCoordinatorService coordinator;

    @BeforeEach
    void setUp() throws Exception {
        taskId = UUID.randomUUID();
        workspace = workspaceRoot.resolve(taskId.toString());
        Files.createDirectories(workspace.resolve(".git"));

        analysis = mock(ProjectAnalysisService.class);
        modification = mock(CodeModificationService.class);
        publisher = mock(CodeModifiedEventPublisher.class);
        stateStore = new RetryStateStore();

        coordinator = new RetryCoordinatorService(
                analysis, modification, publisher, stateStore,
                workspaceRoot.toString(), 2
        );

        when(analysis.analyze(eq(taskId), eq("owner/repository"), anyString(), eq(workspace)))
                .thenReturn(new ProjectAnalysisResult(
                        "mock", "mock", true, 0, 0, 1, 1,
                        workspace.resolve(".devflow/repo-map.md"),
                        List.of("src/App.java"),
                        workspace.resolve(".devflow/analysis.md"),
                        "Mock analysis"
                ));

        when(modification.modify(
                eq(taskId), eq("owner/repository"), anyString(),
                eq(workspace), eq(List.of("src/App.java"))
        )).thenReturn(new CodeModificationResult(
                "devflow/" + taskId, "mock", "mock", true, 0, 0,
                List.of("src/App.java"),
                workspace.resolve(".devflow/change-plan.md"),
                workspace.resolve(".devflow/changes.diff"),
                "diff"
        ));
    }

    @Test
    void retriesTwiceThenStopsAndSkipsDuplicates() throws Exception {
        TestResultEvent failure1 = event("TEST_FAILED", 1);
        TestResultEvent failure2 = event("TEST_FAILED", 2);
        TestResultEvent failure3 = event("TEST_FAILED", 3);

        assertEquals(RetryOutcome.RETRY_PUBLISHED, coordinator.handle(failure1));
        assertEquals(RetryOutcome.DUPLICATE, coordinator.handle(failure1));
        assertEquals(RetryOutcome.RETRY_PUBLISHED, coordinator.handle(failure2));
        assertEquals(RetryOutcome.MAX_RETRIES_REACHED, coordinator.handle(failure3));
        assertEquals(RetryOutcome.IGNORED, coordinator.handle(failure3));

        RetryState state = stateStore.read(workspace);
        assertEquals(2, state.attempts());
        assertEquals("MAX_RETRIES_REACHED", state.status());
        assertTrue(Files.readString(workspace.resolve(".devflow/retry-history.md"))
                .contains("MAX_RETRIES_REACHED"));

        verify(publisher, times(2)).publish(
                eq(taskId),
                eq("owner/repository"),
                eq("Fix failing build"),
                eq(workspace),
                eq("devflow/" + taskId),
                eq(List.of("src/App.java"))
        );
    }

    @Test
    void passingTestCompletesWithoutRetry() {
        assertEquals(RetryOutcome.COMPLETED, coordinator.handle(event("TEST_PASSED", 1)));
        assertEquals("COMPLETED", stateStore.read(workspace).status());
        assertEquals(RetryOutcome.IGNORED, coordinator.handle(event("TEST_FAILED", 2)));
        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsResultPointingOutsideTaskWorkspace() {
        TestResultEvent original = event("TEST_FAILED", 1);
        TestResultEvent forged = new TestResultEvent(
                original.eventType(),
                original.taskId(),
                original.repository(),
                original.prompt(),
                workspaceRoot.toString(),
                original.branch(),
                original.changedFiles(),
                original.status(),
                original.command(),
                original.exitCode(),
                original.durationMs(),
                original.outputTail(),
                original.resultFile(),
                original.createdAt()
        );

        assertThrows(IllegalArgumentException.class, () -> coordinator.handle(forged));
        verifyNoInteractions(publisher);
    }

    private TestResultEvent event(String status, int second) {
        return new TestResultEvent(
                status, taskId, "owner/repository", "Fix failing build",
                workspace.toString(), "devflow/" + taskId,
                List.of("src/App.java"), status,
                "mvn verify", "TEST_FAILED".equals(status) ? 1 : 0,
                100L, "BUILD FAILURE: cannot find symbol",
                workspace.resolve(".devflow/test-result.md").toString(),
                Instant.parse("2026-10-07T05:00:00Z").plusSeconds(second)
        );
    }
}
