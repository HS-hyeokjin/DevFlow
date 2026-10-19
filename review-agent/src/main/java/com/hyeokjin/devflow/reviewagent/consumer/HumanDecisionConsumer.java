package com.hyeokjin.devflow.reviewagent.consumer;

import com.hyeokjin.devflow.reviewagent.event.HumanDecisionEvent;
import com.hyeokjin.devflow.reviewagent.service.HumanDecisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class HumanDecisionConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(HumanDecisionConsumer.class);

    private final HumanDecisionService service;

    public HumanDecisionConsumer(HumanDecisionService service) {
        this.service = service;
    }

    @KafkaListener(
            topics = "${devflow.kafka.topics.human-decision}",
            containerFactory = "humanDecisionKafkaListenerContainerFactory"
    )
    public void consume(HumanDecisionEvent event) {
        log.info(
                "[HUMAN DECISION RECEIVED] taskId={}, approved={}, decidedBy={}",
                event.taskId(),
                event.approved(),
                event.decidedBy()
        );

        String outcome = service.handle(event);

        log.info(
                "[HUMAN DECISION COMPLETED] taskId={}, outcome={}",
                event.taskId(),
                outcome
        );
    }
}
