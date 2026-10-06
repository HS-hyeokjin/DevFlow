package com.hyeokjin.devflow.codeagent.analysis.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MockAiClientTest {

    @Test
    void returnsFreeMockResponseWithoutExternalApi() {
        MockAiClient client = new MockAiClient(0);

        AiAnalysisResponse response = client.analyze("프로젝트 구조를 분석해줘");

        assertEquals("mock", response.provider());
        assertEquals("mock-devflow-v1", response.model());
        assertTrue(response.mock());
        assertTrue(response.inputTokens() > 0);
        assertTrue(response.outputTokens() > 0);
        assertTrue(response.content().contains("Mock AI"));
    }
}
