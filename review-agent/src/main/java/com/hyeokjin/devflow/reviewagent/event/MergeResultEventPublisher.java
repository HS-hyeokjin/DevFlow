package com.hyeokjin.devflow.reviewagent.event;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class MergeResultEventPublisher {

    private final KafkaTemplate<String, MergeResultEvent> kafkaTemplate;
    private final String topic;

    public MergeResultEventPublisher(
            KafkaTemplate<String, MergeResultEvent> kafkaTemplate,
            @Value("${devflow.kafka.topics.merge-result}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(
            HumanDecisionEvent decision,
            String eventType,
            boolean merged,
            String message
    ) {
        MergeResultEvent event = new MergeResultEvent(
                eventType,
                decision.taskId(),
                decision.repository(),
                decision.branch(),
                decision.pullRequestNumber(),
                decision.pullRequestUrl(),
                merged,
                message == null ? "" : message,
                Instant.now()
        );

        kafkaTemplate.send(
                topic,
                decision.taskId().toString(),
                event
        ).join();
    }
}
