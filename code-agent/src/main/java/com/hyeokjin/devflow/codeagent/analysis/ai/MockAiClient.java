package com.hyeokjin.devflow.codeagent.analysis.ai;

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
public class MockAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(MockAiClient.class);

    private final long delayMs;

    public MockAiClient(
            @Value("${devflow.ai.mock.delay-ms:300}") long delayMs
    ) {
        this.delayMs = Math.max(0, delayMs);
    }

    @Override
    public AiAnalysisResponse analyze(String prompt) {
        sleep();

        int inputTokens = estimateTokens(prompt);

        String content = """
                # Mock AI 프로젝트 분석

                > 이 결과는 실제 LLM 호출 결과가 아니라 DevFlow 파이프라인 검증용 Mock 응답입니다.

                ## 1. 프로젝트 한줄 요약
                DevFlow는 작업 요청을 Kafka 이벤트로 전달하고, Code Agent가 Git 저장소를 복제한 뒤 프로젝트를 분석하는 AI 개발 에이전트 실습 프로젝트입니다.

                ## 2. 기술 스택
                - Java 21
                - Spring Boot
                - Apache Kafka
                - Eclipse JGit
                - Maven

                ## 3. 현재 처리 흐름
                Task Service → Kafka → Code Agent → Git Clone → Project Scanner → AI Client → analysis.md

                ## 4. 현재 검증 가능한 항목
                - Kafka 이벤트 소비
                - Private GitHub 저장소 clone
                - Workspace 생성 및 재사용
                - 프로젝트 주요 파일 수집
                - AI 호출 추상화
                - 분석 결과 파일 저장

                ## 5. Mock 모드의 목적
                실제 API 비용 없이 AI 호출 전후의 전체 시스템 흐름, 실패 처리, 저장 로직을 반복 테스트합니다.

                ## 6. 다음 단계 제안
                Repo Map과 관련 파일 선택 기능을 추가한 뒤, 실제 AI를 사용할 때 전체 프로젝트 대신 필요한 코드만 전달하는 구조로 개선합니다.
                """;

        int outputTokens = estimateTokens(content);

        log.info(
                """
                
                ============================================================
                [MOCK AI RESPONSE]
                Provider      : mock
                Model         : mock-devflow-v1
                Input Tokens  : {} (estimated)
                Output Tokens : {} (estimated)
                Cost          : $0.00
                Delay         : {} ms
                ============================================================
                """,
                inputTokens,
                outputTokens,
                delayMs
        );

        return new AiAnalysisResponse(
                "mock",
                "mock-devflow-v1",
                content,
                inputTokens,
                outputTokens,
                true
        );
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
            throw new IllegalStateException("Mock AI call was interrupted", e);
        }
    }
}
