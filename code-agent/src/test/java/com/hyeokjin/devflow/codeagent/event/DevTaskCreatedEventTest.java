package com.hyeokjin.devflow.codeagent.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DevTaskCreatedEventTest {

    @Test
    void keepsRepository() {
        DevTaskCreatedEvent event = new DevTaskCreatedEvent(
                "DEV_TASK_CREATED",
                UUID.randomUUID(),
                "HS-hyeokjin/DevFlow",
                "프로젝트 분석해줘",
                Instant.now()
        );

        assertEquals("HS-hyeokjin/DevFlow", event.repository());
    }
}
