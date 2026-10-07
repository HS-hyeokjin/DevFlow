package com.hyeokjin.devflow.codeagent.event;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class CodeModifiedEventPublisher {

    private final KafkaTemplate<String, CodeModifiedEvent> kafkaTemplate;
    private final String topic;

    public CodeModifiedEventPublisher(
            KafkaTemplate<String, CodeModifiedEvent> kafkaTemplate,
            @Value("${devflow.kafka.topics.code-modified}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(
            UUID taskId,
            String repository,
            String prompt,
            Path workspace,
            String branch,
            List<String> changedFiles
    ) {
        CodeModifiedEvent event = new CodeModifiedEvent(
                "CODE_MODIFIED",
                taskId,
                repository,
                prompt,
                workspace.toAbsolutePath().normalize().toString(),
                branch,
                List.copyOf(changedFiles),
                Instant.now()
        );

        kafkaTemplate.send(topic, taskId.toString(), event).join();
    }
}
