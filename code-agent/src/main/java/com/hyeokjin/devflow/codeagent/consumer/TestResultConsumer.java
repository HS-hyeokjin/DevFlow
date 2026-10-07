package com.hyeokjin.devflow.codeagent.consumer;

import com.hyeokjin.devflow.codeagent.event.TestResultEvent;
import com.hyeokjin.devflow.codeagent.retry.RetryCoordinatorService;
import com.hyeokjin.devflow.codeagent.retry.RetryOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TestResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(TestResultConsumer.class);

    private final RetryCoordinatorService coordinator;

    public TestResultConsumer(RetryCoordinatorService coordinator) {
        this.coordinator = coordinator;
    }

    @KafkaListener(
            topics = "${devflow.kafka.topics.test-result}",
            containerFactory = "testResultKafkaListenerContainerFactory"
    )
    public void consume(TestResultEvent event) {
        log.info("[TEST RESULT RECEIVED] taskId={}, status={}, exitCode={}",
                event.taskId(), event.status(), event.exitCode());

        RetryOutcome outcome = coordinator.handle(event);

        log.info("[TEST RESULT HANDLED] taskId={}, outcome={}", event.taskId(), outcome);
    }
}
