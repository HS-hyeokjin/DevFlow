package com.hyeokjin.devflow.codeagent.consumer;

import com.hyeokjin.devflow.codeagent.event.DevTaskCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DevTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(DevTaskConsumer.class);

    @KafkaListener(topics = "${devflow.kafka.topics.dev-task-created}")
    public void consume(DevTaskCreatedEvent event) {
        log.info(
                """
                
                ============================================================
                [TASK RECEIVED]
                Task ID    : {}
                Repository : {}
                Prompt     : {}
                Created At : {}
                ============================================================
                """,
                event.taskId(),
                event.repository(),
                event.prompt(),
                event.createdAt()
        );
    }
}
