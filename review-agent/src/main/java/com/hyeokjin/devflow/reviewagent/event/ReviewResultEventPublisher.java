package com.hyeokjin.devflow.reviewagent.event;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReviewResultEventPublisher {

    private final KafkaTemplate<String, ReviewResultEvent> kafkaTemplate;
    private final String topic;

    public ReviewResultEventPublisher(
            KafkaTemplate<String, ReviewResultEvent> kafkaTemplate,
            @Value("${devflow.kafka.topics.review-result}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(
            UUID taskId,
            String repository,
            String branch,
            boolean approved,
            String summary,
            String pullRequestUrl
    ) {
        String eventType = pullRequestUrl != null && !pullRequestUrl.isBlank()
                ? "PULL_REQUEST_CREATED"
                : approved ? "REVIEW_APPROVED" : "REVIEW_REJECTED";

        ReviewResultEvent event = new ReviewResultEvent(
                eventType,
                taskId,
                repository,
                branch,
                approved,
                summary,
                pullRequestUrl == null ? "" : pullRequestUrl,
                Instant.now()
        );

        kafkaTemplate.send(topic, taskId.toString(), event).join();
    }
}
