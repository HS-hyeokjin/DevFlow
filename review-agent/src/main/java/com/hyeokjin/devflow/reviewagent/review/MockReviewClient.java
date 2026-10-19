package com.hyeokjin.devflow.reviewagent.review;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "devflow.review",
        name = "provider",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockReviewClient implements ReviewClient {

    @Override
    public ReviewDecision review(ReviewRequest request) {
        boolean hasDiff = request.diff() != null && !request.diff().isBlank();
        boolean hasFiles = request.changedFiles() != null && !request.changedFiles().isEmpty();
        boolean testPassed = request.testExitCode() == 0;
        boolean approved = hasDiff && hasFiles && testPassed;

        List<String> findings = approved
                ? List.of(
                        "테스트 성공 결과 확인",
                        "변경 파일 manifest 확인",
                        "Mock Review는 코드 품질 판단 대신 파이프라인 조건만 검증"
                )
                : List.of(
                        !hasDiff ? "Git diff가 비어 있음" : "",
                        !hasFiles ? "변경 파일 manifest가 비어 있음" : "",
                        !testPassed ? "테스트 exit code가 0이 아님" : ""
                ).stream().filter(item -> !item.isBlank()).toList();

        String summary = approved
                ? "Mock review approved: 테스트 통과 및 변경 diff/manifest 확인"
                : "Mock review rejected: PR 생성 조건 미충족";

        return new ReviewDecision(
                approved,
                "mock",
                "mock-devflow-review-v1",
                true,
                summary,
                findings,
                estimateTokens(request.diff()),
                estimateTokens(summary + findings)
        );
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
