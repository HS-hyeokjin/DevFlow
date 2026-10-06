package com.hyeokjin.devflow.codeagent.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ProjectScanner {

    private static final int MAX_FILES = 40;
    private static final int MAX_SELECTED_FILES = 8;
    private static final int MAX_TOTAL_CHARACTERS = 80_000;
    private static final int MAX_SELECTED_CHARACTERS = 40_000;
    private static final int MAX_CHARACTERS_PER_FILE = 12_000;

    private static final Set<String> INCLUDED_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts",
            ".xml", ".yml", ".yaml", ".properties",
            ".md", ".txt", ".json",
            ".gradle", ".sql", ".js", ".ts", ".tsx", ".jsx",
            ".py", ".go"
    );

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", "target", "build", "node_modules", ".idea", ".gradle", ".devflow"
    );

    public ProjectSnapshot scan(Path workspace) {
        try (Stream<Path> paths = Files.walk(workspace)) {
            List<Path> candidates = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(workspace, path))
                    .filter(this::isSupported)
                    .sorted(Comparator
                            .comparingInt((Path path) -> priority(workspace.relativize(path).toString()))
                            .thenComparing(path -> workspace.relativize(path).toString()))
                    .toList();

            return readFiles(workspace, candidates, MAX_FILES, MAX_TOTAL_CHARACTERS);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan project workspace: " + workspace, e);
        }
    }

    public ProjectSnapshot scanSelected(
            Path workspace,
            List<String> relativePaths
    ) {
        List<Path> selected = relativePaths.stream()
                .limit(MAX_SELECTED_FILES)
                .map(workspace::resolve)
                .map(Path::normalize)
                .filter(path -> path.startsWith(workspace.normalize()))
                .filter(Files::isRegularFile)
                .toList();

        return readFiles(
                workspace,
                selected,
                MAX_SELECTED_FILES,
                MAX_SELECTED_CHARACTERS
        );
    }

    private ProjectSnapshot readFiles(
            Path workspace,
            List<Path> candidates,
            int maxFiles,
            int maxTotalCharacters
    ) {
        try {
            List<ProjectFileSnapshot> selected = new ArrayList<>();
            int totalCharacters = 0;

            for (Path path : candidates) {
                if (selected.size() >= maxFiles || totalCharacters >= maxTotalCharacters) {
                    break;
                }

                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (content.length() > MAX_CHARACTERS_PER_FILE) {
                    content = content.substring(0, MAX_CHARACTERS_PER_FILE)
                            + "\n\n[TRUNCATED BY DEVFLOW]";
                }

                int remaining = maxTotalCharacters - totalCharacters;
                if (content.length() > remaining) {
                    content = content.substring(0, Math.max(0, remaining))
                            + "\n\n[TRUNCATED BY DEVFLOW]";
                }

                String relativePath = workspace.relativize(path)
                        .toString()
                        .replace('\\', '/');

                selected.add(new ProjectFileSnapshot(relativePath, content));
                totalCharacters += content.length();
            }

            return new ProjectSnapshot(List.copyOf(selected), totalCharacters);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read project files", e);
        }
    }

    private boolean isIgnored(Path workspace, Path file) {
        Path relative = workspace.relativize(file);
        for (Path part : relative) {
            if (IGNORED_DIRECTORIES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase();

        if (name.equals("dockerfile")
                || name.equals("pom.xml")
                || name.equals("readme.md")
                || name.equals("mvnw")
                || name.equals("mvnw.cmd")) {
            return true;
        }

        return INCLUDED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private int priority(String path) {
        String normalized = path.replace('\\', '/').toLowerCase();

        if (normalized.equals("readme.md")) return 0;
        if (normalized.equals("pom.xml")
                || normalized.equals("build.gradle")
                || normalized.equals("build.gradle.kts")
                || normalized.equals("settings.gradle")
                || normalized.equals("settings.gradle.kts")) return 1;
        if (normalized.endsWith("/pom.xml")) return 2;
        if (normalized.endsWith("application.yml")
                || normalized.endsWith("application.yaml")
                || normalized.endsWith("application.properties")
                || normalized.equals("docker-compose.yml")
                || normalized.equals("docker-compose.yaml")) return 3;
        if (normalized.endsWith(".java") || normalized.endsWith(".kt")) return 4;
        return 10;
    }
}
