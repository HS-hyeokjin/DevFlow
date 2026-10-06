package com.hyeokjin.devflow.codeagent.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RelevantFileSelector {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}_$]+");

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "to", "of", "in", "for",
            "이", "가", "은", "는", "을", "를", "에", "에서", "하고", "그리고",
            "해줘", "수정", "추가", "변경", "프로젝트", "코드", "파일"
    );

    private static final Map<String, List<String>> SYNONYMS = buildSynonyms();

    private final int maxFiles;

    public RelevantFileSelector(
            @Value("${devflow.analysis.max-relevant-files:8}") int maxFiles
    ) {
        this.maxFiles = Math.max(1, maxFiles);
    }

    public RelevantFileSelection select(
            Path workspace,
            String userRequest,
            RepoMapResult repoMap
    ) {
        Set<String> keywords = expandKeywords(userRequest);
        List<RelevantFileSelection.ScoredFile> scored = new ArrayList<>();

        for (RepoMapEntry entry : repoMap.entries()) {
            int score = scoreEntry(workspace, entry, keywords);
            scored.add(new RelevantFileSelection.ScoredFile(entry.path(), score));
        }

        List<RelevantFileSelection.ScoredFile> selectedScores = scored.stream()
                .sorted((left, right) -> {
                    int scoreCompare = Integer.compare(right.score(), left.score());
                    return scoreCompare != 0
                            ? scoreCompare
                            : left.path().compareTo(right.path());
                })
                .limit(maxFiles)
                .toList();

        List<String> paths = selectedScores.stream()
                .map(RelevantFileSelection.ScoredFile::path)
                .toList();

        return new RelevantFileSelection(paths, selectedScores);
    }

    private int scoreEntry(
            Path workspace,
            RepoMapEntry entry,
            Set<String> keywords
    ) {
        String path = entry.path().toLowerCase(Locale.ROOT);
        String symbols = entry.symbols().toLowerCase(Locale.ROOT);

        int score = structuralScore(path);

        for (String keyword : keywords) {
            if (path.contains(keyword)) {
                score += 14;
            }
            if (symbols.contains(keyword)) {
                score += 10;
            }
        }

        Path file = workspace.resolve(entry.path()).normalize();
        if (file.startsWith(workspace.normalize())) {
            score += contentScore(file, keywords);
        }

        return score;
    }

    private int contentScore(Path file, Set<String> keywords) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);

            int score = 0;
            for (String keyword : keywords) {
                int index = 0;
                int matches = 0;

                while ((index = content.indexOf(keyword, index)) >= 0 && matches < 5) {
                    matches++;
                    index += keyword.length();
                }

                score += matches * 2;
            }
            return score;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private int structuralScore(String path) {
        if (path.equals("pom.xml")
                || path.equals("build.gradle")
                || path.equals("build.gradle.kts")) {
            return 5;
        }
        if (path.endsWith("/pom.xml")) {
            return 4;
        }
        if (path.equals("readme.md")) {
            return 3;
        }
        if (path.endsWith("application.yml")
                || path.endsWith("application.yaml")
                || path.endsWith("application.properties")) {
            return 4;
        }
        return 0;
    }

    private Set<String> expandKeywords(String request) {
        Set<String> keywords = new HashSet<>();

        if (request != null) {
            for (String token : TOKEN_SPLIT.split(request.toLowerCase(Locale.ROOT))) {
                String normalized = token.trim();
                if (normalized.length() >= 2 && !STOP_WORDS.contains(normalized)) {
                    keywords.add(normalized);
                }
            }
        }

        Set<String> expanded = new HashSet<>(keywords);
        for (String keyword : keywords) {
            SYNONYMS.forEach((trigger, values) -> {
                if (keyword.contains(trigger) || trigger.contains(keyword)) {
                    expanded.addAll(values);
                }
            });
        }

        return expanded;
    }

    private static Map<String, List<String>> buildSynonyms() {
        Map<String, List<String>> synonyms = new LinkedHashMap<>();

        synonyms.put("로그인", List.of("login", "auth", "authentication", "security", "jwt", "oauth"));
        synonyms.put("인증", List.of("auth", "authentication", "security", "jwt", "oauth"));
        synonyms.put("회원", List.of("user", "member", "account", "profile"));
        synonyms.put("사용자", List.of("user", "member", "account"));
        synonyms.put("결제", List.of("payment", "pay", "billing", "escrow"));
        synonyms.put("카프카", List.of("kafka", "producer", "consumer", "listener", "topic"));
        synonyms.put("이벤트", List.of("event", "kafka", "producer", "consumer"));
        synonyms.put("깃", List.of("git", "github", "clone", "repository"));
        synonyms.put("저장소", List.of("git", "github", "clone", "repository"));
        synonyms.put("테스트", List.of("test", "junit", "verify"));
        synonyms.put("설정", List.of("config", "configuration", "application", "properties", "yaml", "yml"));
        synonyms.put("분석", List.of("analysis", "analyze", "scanner", "map"));
        synonyms.put("ai", List.of("ai", "openai", "llm", "mock", "analysis"));
        synonyms.put("게이트웨이", List.of("gateway", "route", "routing", "spring", "cloud"));
        synonyms.put("gateway", List.of("gateway", "route", "routing"));
        synonyms.put("오류", List.of("error", "exception", "fail", "failure"));
        synonyms.put("에러", List.of("error", "exception", "fail", "failure"));

        return Map.copyOf(synonyms);
    }
}
