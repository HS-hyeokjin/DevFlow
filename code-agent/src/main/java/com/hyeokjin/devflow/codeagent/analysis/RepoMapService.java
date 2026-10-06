package com.hyeokjin.devflow.codeagent.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class RepoMapService {

    private static final int MAX_FILES = 2_000;
    private static final int MAX_SYMBOL_SCAN_CHARACTERS = 20_000;

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

    private static final Pattern JAVA_TYPE_PATTERN = Pattern.compile(
            "\\b(class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
    );
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "\\bpackage\\s+([A-Za-z0-9_.]+)"
    );
    private static final Pattern JS_EXPORT_PATTERN = Pattern.compile(
            "\\b(?:export\\s+)?(?:class|function|interface|type)\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
    );
    private static final Pattern PYTHON_SYMBOL_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:class|def)\\s+([A-Za-z_][A-Za-z0-9_]*)"
    );

    public RepoMapResult build(Path workspace) {
        try {
            List<RepoMapEntry> entries = new ArrayList<>();

            try (Stream<Path> paths = Files.walk(workspace)) {
                for (Path file : paths
                        .filter(Files::isRegularFile)
                        .filter(path -> !isIgnored(workspace, path))
                        .filter(this::isSupported)
                        .sorted()
                        .limit(MAX_FILES)
                        .toList()) {
                    entries.add(toEntry(workspace, file));
                }
            }

            Path outputFile = save(workspace, entries);
            return new RepoMapResult(List.copyOf(entries), outputFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build repository map: " + workspace, e);
        }
    }

    private RepoMapEntry toEntry(Path workspace, Path file) throws IOException {
        String relativePath = workspace.relativize(file)
                .toString()
                .replace('\\', '/');

        long size = Files.size(file);
        String content = Files.readString(file, StandardCharsets.UTF_8);
        int lineCount = content.isEmpty() ? 0 : (int) content.lines().count();

        String symbolSource = content.length() > MAX_SYMBOL_SCAN_CHARACTERS
                ? content.substring(0, MAX_SYMBOL_SCAN_CHARACTERS)
                : content;

        return new RepoMapEntry(
                relativePath,
                detectType(file),
                extractSymbols(file, symbolSource),
                lineCount,
                size
        );
    }

    private String extractSymbols(Path file, String content) {
        String name = file.getFileName().toString().toLowerCase();
        List<String> symbols = new ArrayList<>();

        if (name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".kts")) {
            Matcher packageMatcher = PACKAGE_PATTERN.matcher(content);
            if (packageMatcher.find()) {
                symbols.add("package=" + packageMatcher.group(1));
            }
            collectMatches(JAVA_TYPE_PATTERN.matcher(content), symbols, 2);
        } else if (name.endsWith(".js")
                || name.endsWith(".ts")
                || name.endsWith(".tsx")
                || name.endsWith(".jsx")) {
            collectMatches(JS_EXPORT_PATTERN.matcher(content), symbols, 1);
        } else if (name.endsWith(".py")) {
            collectMatches(PYTHON_SYMBOL_PATTERN.matcher(content), symbols, 1);
        }

        return symbols.isEmpty()
                ? "-"
                : String.join(", ", symbols.stream().distinct().limit(12).toList());
    }

    private void collectMatches(Matcher matcher, List<String> target, int group) {
        while (matcher.find() && target.size() < 12) {
            target.add(matcher.group(group));
        }
    }

    private Path save(Path workspace, List<RepoMapEntry> entries) throws IOException {
        Path outputDirectory = workspace.resolve(".devflow");
        Files.createDirectories(outputDirectory);

        Path outputFile = outputDirectory.resolve("repo-map.md");

        StringBuilder builder = new StringBuilder();
        builder.append("# DevFlow Repository Map\n\n");
        builder.append("Files indexed: ").append(entries.size()).append("\n\n");
        builder.append("| Path | Type | Symbols | Lines | Bytes |\n");
        builder.append("| --- | --- | --- | ---: | ---: |\n");

        for (RepoMapEntry entry : entries) {
            builder.append("| ")
                    .append(escape(entry.path()))
                    .append(" | ")
                    .append(escape(entry.type()))
                    .append(" | ")
                    .append(escape(entry.symbols()))
                    .append(" | ")
                    .append(entry.lineCount())
                    .append(" | ")
                    .append(entry.sizeBytes())
                    .append(" |\n");
        }

        Files.writeString(outputFile, builder.toString(), StandardCharsets.UTF_8);
        return outputFile.toAbsolutePath().normalize();
    }

    private String escape(String value) {
        return value.replace("|", "\\|").replace("\n", " ");
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

    private String detectType(Path path) {
        String name = path.getFileName().toString().toLowerCase();

        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".kt") || name.endsWith(".kts")) return "kotlin";
        if (name.endsWith(".xml")) return "xml";
        if (name.endsWith(".yml") || name.endsWith(".yaml")) return "yaml";
        if (name.endsWith(".properties")) return "properties";
        if (name.endsWith(".md")) return "markdown";
        if (name.endsWith(".json")) return "json";
        if (name.endsWith(".js") || name.endsWith(".jsx")) return "javascript";
        if (name.endsWith(".ts") || name.endsWith(".tsx")) return "typescript";
        if (name.endsWith(".py")) return "python";
        if (name.endsWith(".go")) return "go";
        if (name.endsWith(".sql")) return "sql";
        return "text";
    }
}
