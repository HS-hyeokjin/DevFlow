package com.hyeokjin.devflow.testagent.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class TestCommandResolver {

    public TestCommand resolve(Path workspace) {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");

        if (windows && Files.isRegularFile(workspace.resolve("mvnw.cmd"))) {
            return new TestCommand(
                    "mvnw.cmd -B -ntp verify",
                    List.of("cmd.exe", "/c", "mvnw.cmd", "-B", "-ntp", "verify")
            );
        }

        if (!windows && Files.isRegularFile(workspace.resolve("mvnw"))) {
            return new TestCommand(
                    "./mvnw -B -ntp verify",
                    List.of("./mvnw", "-B", "-ntp", "verify")
            );
        }

        if (windows && Files.isRegularFile(workspace.resolve("gradlew.bat"))) {
            return new TestCommand(
                    "gradlew.bat test",
                    List.of("cmd.exe", "/c", "gradlew.bat", "test")
            );
        }

        if (!windows && Files.isRegularFile(workspace.resolve("gradlew"))) {
            return new TestCommand(
                    "./gradlew test",
                    List.of("./gradlew", "test")
            );
        }

        if (Files.isRegularFile(workspace.resolve("pom.xml"))) {
            return new TestCommand(
                    "mvn -B -ntp verify",
                    List.of("mvn", "-B", "-ntp", "verify")
            );
        }

        if (Files.isRegularFile(workspace.resolve("build.gradle"))
                || Files.isRegularFile(workspace.resolve("build.gradle.kts"))) {
            return new TestCommand(
                    "gradle test",
                    List.of("gradle", "test")
            );
        }

        throw new IllegalStateException(
                "No supported Maven or Gradle build configuration found in " + workspace
        );
    }
}
