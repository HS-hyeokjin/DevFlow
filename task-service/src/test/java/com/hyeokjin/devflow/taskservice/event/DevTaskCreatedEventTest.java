package com.hyeokjin.devflow.taskservice.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DevTaskCreatedEventTest {

    @Test
    void keepsTaskIdentity() {
        UUID taskId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        DevTaskCreatedEvent event = new DevTaskCreatedEvent(
                "DEV_TASK_CREATED",
                taskId,
                "HS-hyeokjin/DevFlow",
                "프로젝트 분석해줘",
                createdAt
        );

        assertEquals(taskId, event.taskId());
        assertEquals("DEV_TASK_CREATED", event.eventType());
    }
}
