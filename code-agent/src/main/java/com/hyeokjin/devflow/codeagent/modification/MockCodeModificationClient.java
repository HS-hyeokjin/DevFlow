package com.hyeokjin.devflow.codeagent.modification;

import com.hyeokjin.devflow.codeagent.analysis.ProjectFileSnapshot;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "devflow.ai",
        name = "provider",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockCodeModificationClient implements CodeModificationClient {

    private static final Logger log = LoggerFactory.getLogger(MockCodeModificationClient.class);
    private static final String MARKER = "DEVFLOW-MOCK-CHANGE";

    private final long delayMs;

    public MockCodeModificationClient(
            @Value("${devflow.ai.mock.delay-ms:300}") long delayMs
    ) {
        this.delayMs = Math.max(0, delayMs);
    }

    @Override
    public CodeModificationProposal propose(
            String repository,
            String userRequest,
            List<ProjectFileSnapshot> files
    ) {
        sleep();

        ProjectFileSnapshot target = files.stream()
                .filter(file -> supportsSafeComment(file.path()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Mock modification could not find a safely editable selected file"
                ));

        String request = sanitize(userRequest);
        String changedContent = applyMockChange(target, request);

        String summary = "Mock mode: selected file에 안전한 주석 변경을 적용해 "
                + "실제 파일 수정 → git diff 파이프라인을 검증합니다.";

        int inputTokens = estimateTokens(
                repository + "\n" + userRequest + "\n" + target.content()
        );
        int outputTokens = estimateTokens(changedContent);

        log.info(
                """
                
                ============================================================
                [MOCK CODE MODIFICATION]
                Target File   : {}
                Input Tokens  : {} (estimated)
                Output Tokens : {} (estimated)
                Cost          : $0.00
                ============================================================
                """,
                target.path(),
                inputTokens,
                outputTokens
        );

        return new CodeModificationProposal(
                "mock",
                "mock-devflow-code-v1",
                true,
                summary,
                inputTokens,
                outputTokens,
                List.of(new ProposedFileChange(
                        target.path(),
                        changedContent,
                        "Mock pipeline verification for request: " + request
                ))
        );
    }

    private String applyMockChange(ProjectFileSnapshot file, String request) {
        if (file.content().contains(MARKER)) {
            return file.content();
        }

        String comment = commentFor(file.path(), request);
        return file.content().stripTrailing() + "\n\n" + comment + "\n";
    }

    private boolean supportsSafeComment(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".java")
                || lower.endsWith(".kt")
                || lower.endsWith(".kts")
                || lower.endsWith(".js")
                || lower.endsWith(".jsx")
                || lower.endsWith(".ts")
                || lower.endsWith(".tsx")
                || lower.endsWith(".go")
                || lower.endsWith(".py")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml")
                || lower.endsWith(".properties")
                || lower.endsWith(".xml")
                || lower.endsWith(".md");
    }

    private String commentFor(String path, String request) {
        String lower = path.toLowerCase(Locale.ROOT);
        String message = MARKER + ": " + request;

        if (lower.endsWith(".py")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml")
                || lower.endsWith(".properties")) {
            return "# " + message;
        }

        if (lower.endsWith(".xml") || lower.endsWith(".md")) {
            return "<!-- " + message.replace("--", "-") + " -->";
        }

        return "// " + message;
    }

    private String sanitize(String request) {
        if (request == null || request.isBlank()) {
            return "no request text";
        }

        String oneLine = request.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 160
                ? oneLine
                : oneLine.substring(0, 160);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private void sleep() {
        if (delayMs == 0) {
            return;
        }

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mock code modification was interrupted", e);
        }
    }
}
