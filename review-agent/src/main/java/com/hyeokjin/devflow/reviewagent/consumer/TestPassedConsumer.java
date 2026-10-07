package com.hyeokjin.devflow.reviewagent.consumer;

import com.hyeokjin.devflow.reviewagent.event.TestResultEvent;
import com.hyeokjin.devflow.reviewagent.service.ReviewPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TestPassedConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(TestPassedConsumer.class);

    private final ReviewPipelineService pipelineService;

    public TestPassedConsumer(ReviewPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @KafkaListener(topics = "${devflow.kafka.topics.test-result}")
    public void consume(TestResultEvent event) {
        if (!"TEST_PASSED".equals(event.status())) {
            return;
        }

        log.info(
                "[TEST_PASSED RECEIVED BY REVIEW AGENT] taskId={}, repository={}, branch={}",
                event.taskId(),
                event.repository(),
                event.branch()
        );

        String outcome = pipelineService.process(event);

        log.info(
                "[REVIEW PIPELINE COMPLETED] taskId={}, outcome={}",
                event.taskId(),
                outcome
        );
    }
}
