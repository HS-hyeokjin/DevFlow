package com.hyeokjin.devflow.codeagent.retry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Properties;
import org.springframework.stereotype.Service;

/**
 * Local durable state for a single-machine learning project.
 * Every task owns its own workspace and retry-state.properties file.
 */
@Service
public class RetryStateStore {

    public RetryState read(Path workspace) {
        Path file = workspace.resolve(".devflow/retry-state.properties");
        if (!Files.isRegularFile(file)) {
            return RetryState.initial();
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return new RetryState(
                    Integer.parseInt(properties.getProperty("attempts", "0")),
                    properties.getProperty("lastEventId", ""),
                    properties.getProperty("status", "PENDING")
            );
        } catch (IOException | NumberFormatException e) {
            throw new IllegalStateException("Cannot read retry state: " + file, e);
        }
    }

    public void save(Path workspace, RetryState state) {
        Path directory = workspace.resolve(".devflow");
        Path destination = directory.resolve("retry-state.properties");
        Path temporary = null;

        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, "retry-state-", ".tmp");

            Properties properties = new Properties();
            properties.setProperty("attempts", Integer.toString(state.attempts()));
            properties.setProperty("lastEventId", state.lastEventId());
            properties.setProperty("status", state.status());
            properties.setProperty("updatedAt", Instant.now().toString());

            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "DevFlow retry state");
            }

            try {
                Files.move(
                        temporary, destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot save retry state: " + destination, e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A leftover temp file does not change the committed retry state.
                }
            }
        }
    }

    public void appendHistory(Path workspace, String line) {
        Path directory = workspace.resolve(".devflow");
        Path file = directory.resolve("retry-history.md");

        try {
            Files.createDirectories(directory);
            if (!Files.exists(file)) {
                Files.writeString(
                        file,
                        "# DevFlow Retry History\n\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW
                );
            }
            Files.writeString(
                    file,
                    "- " + Instant.now() + " | " + line + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new IllegalStateException("Cannot append retry history: " + file, e);
        }
    }
}
