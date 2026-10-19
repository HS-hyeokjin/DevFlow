package com.hyeokjin.devflow.reviewagent.github;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class GitHubPullRequestClient {

    private static final Pattern NUMBER =
            Pattern.compile("\"number\"\\s*:\\s*(\\d+)");
    private static final Pattern HTML_URL =
            Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public PullRequestResult findOrCreate(
            String repository,
            String branch,
            String baseBranch,
            String title,
            String body,
            String token
    ) {
        String owner = repository.substring(0, repository.indexOf('/'));

        PullRequestResult existing = findOpen(
                repository,
                owner,
                branch,
                baseBranch,
                token
        );
        if (existing != null) {
            return existing;
        }

        String payload = "{"
                + "\"title\":\"" + escape(title) + "\","
                + "\"head\":\"" + escape(branch) + "\","
                + "\"base\":\"" + escape(baseBranch) + "\","
                + "\"body\":\"" + escape(body) + "\""
                + "}";

        HttpRequest request = baseRequest(
                URI.create("https://api.github.com/repos/" + repository + "/pulls"),
                token
        ).POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = send(request);

        if (response.statusCode() == 201) {
            return parse(response.body(), true);
        }

        if (response.statusCode() == 422) {
            PullRequestResult afterConflict = findOpen(
                    repository,
                    owner,
                    branch,
                    baseBranch,
                    token
            );
            if (afterConflict != null) {
                return afterConflict;
            }
        }

        throw new IllegalStateException(
                "GitHub PR creation failed. status="
                        + response.statusCode()
                        + ", body="
                        + limited(response.body())
        );
    }

    public GitHubMergeResult merge(
            String repository,
            int pullRequestNumber,
            String token
    ) {
        if (pullRequestNumber <= 0) {
            throw new IllegalArgumentException("Invalid pull request number");
        }

        String payload = "{\"merge_method\":\"squash\"}";
        HttpRequest request = baseRequest(
                URI.create(
                        "https://api.github.com/repos/"
                                + repository
                                + "/pulls/"
                                + pullRequestNumber
                                + "/merge"
                ),
                token
        ).PUT(HttpRequest.BodyPublishers.ofString(
                payload,
                StandardCharsets.UTF_8
        )).header("Content-Type", "application/json").build();

        HttpResponse<String> response = send(request);
        boolean merged = response.statusCode() == 200
                && response.body().contains("\"merged\":true");

        if (merged) {
            return new GitHubMergeResult(
                    true,
                    extractString(response.body(), "sha"),
                    extractString(response.body(), "message")
            );
        }

        return new GitHubMergeResult(
                false,
                "",
                response.statusCode() + ": " + limited(response.body())
        );
    }

    private PullRequestResult findOpen(
            String repository,
            String owner,
            String branch,
            String baseBranch,
            String token
    ) {
        String head = URLEncoder.encode(
                owner + ":" + branch,
                StandardCharsets.UTF_8
        );
        String base = URLEncoder.encode(
                baseBranch,
                StandardCharsets.UTF_8
        );

        URI uri = URI.create(
                "https://api.github.com/repos/" + repository
                        + "/pulls?state=open&head=" + head
                        + "&base=" + base
        );

        HttpResponse<String> response = send(
                baseRequest(uri, token).GET().build()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "GitHub PR lookup failed. status=" + response.statusCode()
            );
        }

        String responseBody = response.body().trim();
        if ("[]".equals(responseBody)) {
            return null;
        }

        return parse(responseBody, false);
    }

    private HttpRequest.Builder baseRequest(URI uri, String token) {
        return HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "DevFlow-Review-Agent");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (IOException e) {
            throw new IllegalStateException("GitHub HTTP request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "GitHub HTTP request interrupted",
                    e
            );
        }
    }

    private PullRequestResult parse(String json, boolean created) {
        Matcher number = NUMBER.matcher(json);
        Matcher url = HTML_URL.matcher(json);

        if (!number.find() || !url.find()) {
            throw new IllegalStateException(
                    "GitHub response did not contain PR number/url"
            );
        }

        return new PullRequestResult(
                Integer.parseInt(number.group(1)),
                url.group(1),
                created
        );
    }

    private String extractString(String json, String field) {
        Pattern pattern = Pattern.compile(
                "\\\"" + Pattern.quote(field)
                        + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""
        );
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String limited(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500
                ? value
                : value.substring(0, 500);
    }
}
