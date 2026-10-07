package com.hyeokjin.devflow.codeagent.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RetryStateStoreTest {

    @TempDir
    Path workspace;

    @Test
    void storesAndReloadsAttemptCounter() throws Exception {
        RetryStateStore store = new RetryStateStore();
        assertEquals(0, store.read(workspace).attempts());

        store.save(workspace, new RetryState(2, "event-2", "RETRY_PUBLISHED"));
        store.appendHistory(workspace, "retry event-2");

        RetryState actual = store.read(workspace);
        assertEquals(2, actual.attempts());
        assertEquals("event-2", actual.lastEventId());
        assertEquals("RETRY_PUBLISHED", actual.status());
        assertTrue(Files.readString(workspace.resolve(".devflow/retry-history.md"))
                .contains("retry event-2"));
    }
}
