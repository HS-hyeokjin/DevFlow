package com.hyeokjin.devflow.testagent.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCommandResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void prefersProjectWrapperWhenAvailable() throws Exception {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");

        Path wrapper = tempDir.resolve(windows ? "mvnw.cmd" : "mvnw");
        Files.writeString(wrapper, windows ? "@echo off\r\nexit /b 0\r\n" : "#!/bin/sh\nexit 0\n");
        wrapper.toFile().setExecutable(true);

        TestCommand command = new TestCommandResolver().resolve(tempDir);

        assertTrue(command.display().contains("mvnw"));
    }
}
