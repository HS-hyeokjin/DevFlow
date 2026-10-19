package com.hyeokjin.devflow.taskservice.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskStateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAndReloadsTaskStateAndIgnoresOlderEvents() {
        UUID taskId = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-19T06:00:00Z");
        TaskStateStore store = new TaskStateStore(tempDir.toString());

        store.save(new TaskRecord(
                taskId,
                "owner/repo",
                "fix",
                "QUEUED",
                "",
                0,
                "",
                created,
                created
        ));

        store.update(
                taskId,
                "WAITING_APPROVAL",
                "devflow/" + taskId,
                12,
                "https://github.com/owner/repo/pull/12",
                created.plusSeconds(20)
        );

        store.update(
                taskId,
                "TESTING",
                null,
                null,
                null,
                created.plusSeconds(10)
        );

        TaskStateStore reloaded = new TaskStateStore(tempDir.toString());
        TaskRecord actual = reloaded.find(taskId).orElseThrow();

        assertEquals("WAITING_APPROVAL", actual.status());
        assertEquals(12, actual.pullRequestNumber());
    }
}
