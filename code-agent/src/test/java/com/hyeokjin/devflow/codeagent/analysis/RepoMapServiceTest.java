package com.hyeokjin.devflow.codeagent.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoMapServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsRepositoryMapWithoutUsingAi() throws Exception {
        Path source = tempDir.resolve("src/main/java/example");
        Files.createDirectories(source);
        Files.writeString(
                source.resolve("AuthService.java"),
                "package example; public class AuthService {}"
        );
        Files.writeString(tempDir.resolve("README.md"), "# Sample");

        RepoMapResult result = new RepoMapService().build(tempDir);

        assertEquals(2, result.entries().size());
        assertTrue(Files.exists(result.outputFile()));
        assertTrue(result.entries().stream()
                .anyMatch(entry -> entry.symbols().contains("AuthService")));
    }
}
