package com.hyeokjin.devflow.testagent.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hyeokjin.devflow.testagent.event.CodeModifiedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectTestServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void runsWrapperAndCreatesPassedResult() throws Exception {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");

        Path wrapper = tempDir.resolve(windows ? "mvnw.cmd" : "mvnw");
        Files.writeString(
                wrapper,
                windows
                        ? "@echo off\r\necho BUILD SUCCESS\r\nexit /b 0\r\n"
                        : "#!/bin/sh\necho BUILD SUCCESS\nexit 0\n"
        );
        wrapper.toFile().setExecutable(true);

        CodeModifiedEvent event = new CodeModifiedEvent(
                "CODE_MODIFIED",
                UUID.randomUUID(),
                "owner/repo",
                "테스트해줘",
                tempDir.toString(),
                "devflow/test",
                List.of("App.java"),
                Instant.now()
        );

        ProjectTestService service = new ProjectTestService(
                new TestCommandResolver(),
                10
        );

        ProjectTestResult result = service.run(event);

        assertTrue(result.passed());
        assertEquals("TEST_PASSED", result.status());
        assertTrue(Files.exists(result.resultFile()));
        assertTrue(result.outputTail().contains("BUILD SUCCESS"));
    }
}
