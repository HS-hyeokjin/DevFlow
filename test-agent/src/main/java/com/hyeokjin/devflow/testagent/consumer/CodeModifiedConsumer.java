package com.hyeokjin.devflow.testagent.consumer;

import com.hyeokjin.devflow.testagent.event.CodeModifiedEvent;
import com.hyeokjin.devflow.testagent.event.TestResultEventPublisher;
import com.hyeokjin.devflow.testagent.test.ProjectTestResult;
import com.hyeokjin.devflow.testagent.test.ProjectTestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CodeModifiedConsumer {

    private static final Logger log = LoggerFactory.getLogger(CodeModifiedConsumer.class);

    private final ProjectTestService projectTestService;
    private final TestResultEventPublisher eventPublisher;

    public CodeModifiedConsumer(
            ProjectTestService projectTestService,
            TestResultEventPublisher eventPublisher
    ) {
        this.projectTestService = projectTestService;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = "${devflow.kafka.topics.code-modified}")
    public void consume(CodeModifiedEvent event) {
        log.info(
                """
                
                ============================================================
                [CODE_MODIFIED RECEIVED]
                Task ID       : {}
                Repository    : {}
                Workspace     : {}
                Branch        : {}
                Changed Files : {}
                ============================================================
                """,
                event.taskId(),
                event.repository(),
                event.workspace(),
                event.branch(),
                event.changedFiles()
        );

        log.info("[TEST STARTED] taskId={}", event.taskId());

        ProjectTestResult result = projectTestService.run(event);

        log.info(
                """
                
                ============================================================
                [TEST COMPLETED]
                Task ID     : {}
                Status      : {}
                Command     : {}
                Exit Code   : {}
                Duration    : {} ms
                Result File : {}
                
                {}
                ============================================================
                """,
                event.taskId(),
                result.status(),
                result.command(),
                result.exitCode(),
                result.durationMs(),
                result.resultFile(),
                result.outputTail()
        );

        eventPublisher.publish(event, result);

        log.info(
                "[{} EVENT PUBLISHED] taskId={}",
                result.status(),
                event.taskId()
        );
    }
}
