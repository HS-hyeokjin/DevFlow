package com.hyeokjin.devflow.taskservice.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskStateListTest {

    @TempDir
    Path tempDir;

    @Test
    void listsPersistedTasksNewestFirstAfterRestart() {
        Instant base = Instant.parse("2026-10-19T07:00:00Z");
        UUID olderId = UUID.randomUUID();
        UUID newerId = UUID.randomUUID();

        TaskStateStore store = new TaskStateStore(tempDir.toString());
        store.save(task(olderId, "older", base));
        store.save(task(newerId, "newer", base.plusSeconds(30)));

        TaskStateStore restarted = new TaskStateStore(tempDir.toString());
        var tasks = restarted.list();

        assertEquals(2, tasks.size());
        assertEquals(newerId, tasks.get(0).taskId());
        assertEquals(olderId, tasks.get(1).taskId());
    }

    private TaskRecord task(UUID id, String prompt, Instant time) {
        return new TaskRecord(
                id,
                "owner/repo",
                prompt,
                "QUEUED",
                "",
                0,
                "",
                time,
                time
        );
    }
}
