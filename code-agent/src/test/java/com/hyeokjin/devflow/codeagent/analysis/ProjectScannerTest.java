package com.hyeokjin.devflow.codeagent.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void prioritizesAndReadsProjectFiles() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# Sample");
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");

        Path source = tempDir.resolve("src/main/java/example");
        Files.createDirectories(source);
        Files.writeString(source.resolve("App.java"), "class App {}");

        Path target = tempDir.resolve("target");
        Files.createDirectories(target);
        Files.writeString(target.resolve("ignored.txt"), "ignore me");

        ProjectSnapshot snapshot = new ProjectScanner().scan(tempDir);

        assertEquals("README.md", snapshot.files().getFirst().path());
        assertTrue(snapshot.files().stream().anyMatch(file -> file.path().equals("pom.xml")));
        assertTrue(snapshot.files().stream().anyMatch(file -> file.path().endsWith("App.java")));
        assertTrue(snapshot.files().stream().noneMatch(file -> file.path().contains("target")));
    }
}
