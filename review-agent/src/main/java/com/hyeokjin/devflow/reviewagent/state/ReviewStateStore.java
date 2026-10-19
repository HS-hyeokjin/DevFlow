package com.hyeokjin.devflow.reviewagent.state;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import org.springframework.stereotype.Service;

@Service
public class ReviewStateStore {

    public ReviewState read(Path workspace) {
        Path file = workspace.resolve(".devflow/review-state.properties");
        if (!Files.isRegularFile(file)) {
            return ReviewState.initial();
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);

            return new ReviewState(
                    properties.getProperty("status", "PENDING"),
                    properties.getProperty("commitSha", ""),
                    Integer.parseInt(properties.getProperty("pullRequestNumber", "0")),
                    properties.getProperty("pullRequestUrl", "")
            );
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("Failed to read review state", e);
        }
    }

    public void save(Path workspace, ReviewState state) {
        Path directory = workspace.resolve(".devflow");
        Path file = directory.resolve("review-state.properties");

        try {
            Files.createDirectories(directory);

            Properties properties = new Properties();
            properties.setProperty("status", state.status());
            properties.setProperty("commitSha", state.commitSha());
            properties.setProperty(
                    "pullRequestNumber",
                    Integer.toString(state.pullRequestNumber())
            );
            properties.setProperty("pullRequestUrl", state.pullRequestUrl());
            properties.setProperty("updatedAt", Instant.now().toString());

            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "DevFlow review state");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save review state", e);
        }
    }
}
