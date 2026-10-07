package com.hyeokjin.devflow.testagent.event;

import com.hyeokjin.devflow.testagent.test.ProjectTestResult;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TestResultEventPublisher {

    private final KafkaTemplate<String, TestResultEvent> kafkaTemplate;
    private final String topic;

    public TestResultEventPublisher(
            KafkaTemplate<String, TestResultEvent> kafkaTemplate,
            @Value("${devflow.kafka.topics.test-result}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(
            CodeModifiedEvent source,
            ProjectTestResult result
    ) {
        TestResultEvent event = new TestResultEvent(
                result.status(),
                source.taskId(),
                source.repository(),
                source.prompt(),
                source.workspace(),
                source.branch(),
                source.changedFiles(),
                result.status(),
                result.command(),
                result.exitCode(),
                result.durationMs(),
                result.outputTail(),
                result.resultFile().toString(),
                Instant.now()
        );

        kafkaTemplate.send(topic, source.taskId().toString(), event).join();
    }
}
