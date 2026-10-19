package com.hyeokjin.devflow.taskservice.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hyeokjin.devflow.taskservice.event.CodeModifiedEvent;
import com.hyeokjin.devflow.taskservice.event.MergeResultEvent;
import com.hyeokjin.devflow.taskservice.event.ReviewResultEvent;
import com.hyeokjin.devflow.taskservice.event.TestResultEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskStatusProjectionTest {

    @TempDir
    Path tempDir;

    @Test
    void projectsWorkflowToCompletedStatus() {
        UUID taskId = UUID.randomUUID();
        Instant t = Instant.parse("2026-10-19T06:00:00Z");
        String branch = "devflow/" + taskId;
        TaskStateStore store = new TaskStateStore(tempDir.toString());
        store.save(new TaskRecord(
                taskId, "owner/repo", "fix", "QUEUED", "",
                0, "", t, t
        ));

        TaskStatusProjection projection = new TaskStatusProjection(store);

        projection.onCodeModified(new CodeModifiedEvent(
                "CODE_MODIFIED", taskId, "owner/repo", "fix",
                "/tmp/workspace", branch, List.of("src/App.java"),
                t.plusSeconds(1)
        ));
        assertEquals("TESTING", store.find(taskId).orElseThrow().status());

        projection.onTestResult(new TestResultEvent(
                "TEST_PASSED", taskId, "owner/repo", "fix",
                "/tmp/workspace", branch, List.of("src/App.java"),
                "TEST_PASSED", "mvn verify", 0, 100,
                "BUILD SUCCESS", "/tmp/result",
                t.plusSeconds(2)
        ));
        assertEquals("REVIEWING", store.find(taskId).orElseThrow().status());

        projection.onReviewResult(new ReviewResultEvent(
                "PULL_REQUEST_CREATED", taskId, "owner/repo", branch,
                true, "approved", 7,
                "https://github.com/owner/repo/pull/7",
                t.plusSeconds(3)
        ));
        assertEquals(
                "WAITING_APPROVAL",
                store.find(taskId).orElseThrow().status()
        );

        projection.onMergeResult(new MergeResultEvent(
                "PULL_REQUEST_MERGED", taskId, "owner/repo", branch,
                7, "https://github.com/owner/repo/pull/7",
                true, "merged", t.plusSeconds(4)
        ));
        assertEquals("COMPLETED", store.find(taskId).orElseThrow().status());
    }
}
