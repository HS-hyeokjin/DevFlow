package com.hyeokjin.devflow.testagent.test;

import com.hyeokjin.devflow.testagent.event.CodeModifiedEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProjectTestService {

    private static final int OUTPUT_TAIL_CHARACTERS = 12_000;

    private final TestCommandResolver commandResolver;
    private final long timeoutSeconds;

    public ProjectTestService(
            TestCommandResolver commandResolver,
            @Value("${devflow.test.timeout-seconds:300}") long timeoutSeconds
    ) {
        this.commandResolver = commandResolver;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public ProjectTestResult run(CodeModifiedEvent event) {
        Path workspace = Path.of(event.workspace()).toAbsolutePath().normalize();

        if (!Files.isDirectory(workspace)) {
            return failureBeforeStart(
                    event,
                    workspace,
                    "Workspace does not exist: " + workspace
            );
        }

        TestCommand command;
        try {
            command = commandResolver.resolve(workspace);
        } catch (RuntimeException e) {
            return failureBeforeStart(event, workspace, e.getMessage());
        }

        Path devflowDirectory = workspace.resolve(".devflow");
        Path logFile = devflowDirectory.resolve("test-command.log");

        try {
            Files.createDirectories(devflowDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create test result directory", e);
        }

        long startedAt = System.nanoTime();
        int exitCode;
        boolean finished;

        try {
            Process process = new ProcessBuilder(command.arguments())
                    .directory(workspace.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();

            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }
        } catch (IOException e) {
            return failureBeforeStart(
                    event,
                    workspace,
                    "Failed to start test command: " + e.getMessage()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failureBeforeStart(
                    event,
                    workspace,
                    "Test execution was interrupted"
            );
        }

        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        String outputTail = readTail(logFile);
        boolean passed = finished && exitCode == 0;
        String status = passed ? "TEST_PASSED" : "TEST_FAILED";

        Path resultFile = saveResult(
                event,
                devflowDirectory,
                status,
                command.display(),
                exitCode,
                durationMs,
                outputTail,
                logFile
        );

        return new ProjectTestResult(
                passed,
                status,
                command.display(),
                exitCode,
                durationMs,
                outputTail,
                logFile.toAbsolutePath().normalize(),
                resultFile
        );
    }

    private ProjectTestResult failureBeforeStart(
            CodeModifiedEvent event,
            Path workspace,
            String message
    ) {
        Path devflowDirectory = workspace.resolve(".devflow");

        try {
            Files.createDirectories(devflowDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create test result directory", e);
        }

        Path logFile = devflowDirectory.resolve("test-command.log");
        try {
            Files.writeString(logFile, message, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save test startup failure", e);
        }

        Path resultFile = saveResult(
                event,
                devflowDirectory,
                "TEST_FAILED",
                "unresolved",
                -1,
                0,
                message,
                logFile
        );

        return new ProjectTestResult(
                false,
                "TEST_FAILED",
                "unresolved",
                -1,
                0,
                message,
                logFile.toAbsolutePath().normalize(),
                resultFile
        );
    }

    private Path saveResult(
            CodeModifiedEvent event,
            Path devflowDirectory,
            String status,
            String command,
            int exitCode,
            long durationMs,
            String outputTail,
            Path logFile
    ) {
        Path resultFile = devflowDirectory.resolve("test-result.md");

        String content = """
                # DevFlow Test Result

                - Task ID: %s
                - Repository: %s
                - Branch: %s
                - Status: %s
                - Command: %s
                - Exit code: %d
                - Duration: %d ms
                - Full log: %s

                ## Output Tail

                ```text
                %s
                ```
                """.formatted(
                event.taskId(),
                event.repository(),
                event.branch(),
                status,
                command,
                exitCode,
                durationMs,
                logFile.toAbsolutePath().normalize(),
                sanitizeFence(outputTail)
        );

        try {
            Files.writeString(resultFile, content, StandardCharsets.UTF_8);
            return resultFile.toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save test result", e);
        }
    }

    private String readTail(Path logFile) {
        try {
            String content = Files.exists(logFile)
                    ? Files.readString(logFile, StandardCharsets.UTF_8)
                    : "";

            if (content.length() <= OUTPUT_TAIL_CHARACTERS) {
                return content;
            }

            return "[...output truncated...]\n"
                    + content.substring(content.length() - OUTPUT_TAIL_CHARACTERS);
        } catch (IOException e) {
            return "Failed to read test output: " + e.getMessage();
        }
    }

    private String sanitizeFence(String text) {
        return text == null ? "" : text.replace("```", "'''");
    }
}
