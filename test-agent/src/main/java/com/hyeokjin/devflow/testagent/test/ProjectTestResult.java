package com.hyeokjin.devflow.testagent.test;

import java.nio.file.Path;

public record ProjectTestResult(
        boolean passed,
        String status,
        String command,
        int exitCode,
        long durationMs,
        String outputTail,
        Path logFile,
        Path resultFile
) {
}
