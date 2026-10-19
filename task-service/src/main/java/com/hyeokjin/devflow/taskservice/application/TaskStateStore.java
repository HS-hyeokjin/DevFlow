package com.hyeokjin.devflow.taskservice.application;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaskStateStore {

    private final ConcurrentHashMap<UUID, TaskRecord> cache =
            new ConcurrentHashMap<>();
    private final Path root;

    public TaskStateStore(
            @Value("${devflow.task-state.root:./task-state}") String root
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public synchronized void save(TaskRecord record) {
        cache.put(record.taskId(), record);
        write(record);
    }

    public synchronized Optional<TaskRecord> find(UUID taskId) {
        TaskRecord cached = cache.get(taskId);
        if (cached != null) {
            return Optional.of(cached);
        }

        Path file = file(taskId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        TaskRecord loaded = read(file);
        cache.put(taskId, loaded);
        return Optional.of(loaded);
    }

    public synchronized TaskRecord update(
            UUID taskId,
            String status,
            String branch,
            Integer pullRequestNumber,
            String pullRequestUrl,
            Instant eventTime
    ) {
        TaskRecord current = find(taskId)
                .orElseThrow(() -> new IllegalStateException(
                        "Task not found: " + taskId
                ));

        Instant effectiveTime = eventTime == null
                ? Instant.now()
                : eventTime;

        if (effectiveTime.isBefore(current.updatedAt())) {
            return current;
        }

        TaskRecord updated = new TaskRecord(
                current.taskId(),
                current.repository(),
                current.prompt(),
                status == null ? current.status() : status,
                branch == null ? current.branch() : branch,
                pullRequestNumber == null
                        ? current.pullRequestNumber()
                        : pullRequestNumber,
                pullRequestUrl == null
                        ? current.pullRequestUrl()
                        : pullRequestUrl,
                current.createdAt(),
                effectiveTime
        );

        save(updated);
        return updated;
    }

    private Path file(UUID taskId) {
        return root.resolve(taskId + ".properties").normalize();
    }

    private void write(TaskRecord record) {
        try {
            Files.createDirectories(root);
            Path target = file(record.taskId());
            Path temporary = Files.createTempFile(root, "task-", ".tmp");

            Properties properties = new Properties();
            properties.setProperty("taskId", record.taskId().toString());
            properties.setProperty("repository", record.repository());
            properties.setProperty("prompt", record.prompt());
            properties.setProperty("status", record.status());
            properties.setProperty("branch", record.branch());
            properties.setProperty(
                    "pullRequestNumber",
                    Integer.toString(record.pullRequestNumber())
            );
            properties.setProperty("pullRequestUrl", record.pullRequestUrl());
            properties.setProperty("createdAt", record.createdAt().toString());
            properties.setProperty("updatedAt", record.updatedAt().toString());

            try (Writer writer = Files.newBufferedWriter(
                    temporary,
                    StandardCharsets.UTF_8
            )) {
                properties.store(writer, "DevFlow task state");
            }

            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to persist task state: " + record.taskId(),
                    e
            );
        }
    }

    private TaskRecord read(Path file) {
        try (Reader reader = Files.newBufferedReader(
                file,
                StandardCharsets.UTF_8
        )) {
            Properties properties = new Properties();
            properties.load(reader);

            return new TaskRecord(
                    UUID.fromString(properties.getProperty("taskId")),
                    properties.getProperty("repository", ""),
                    properties.getProperty("prompt", ""),
                    properties.getProperty("status", "UNKNOWN"),
                    properties.getProperty("branch", ""),
                    Integer.parseInt(
                            properties.getProperty("pullRequestNumber", "0")
                    ),
                    properties.getProperty("pullRequestUrl", ""),
                    Instant.parse(properties.getProperty("createdAt")),
                    Instant.parse(properties.getProperty("updatedAt"))
            );
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "Failed to read task state: " + file,
                    e
            );
        }
    }
}
