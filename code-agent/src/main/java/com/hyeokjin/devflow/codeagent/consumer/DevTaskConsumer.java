package com.hyeokjin.devflow.codeagent.consumer;

import com.hyeokjin.devflow.codeagent.event.DevTaskCreatedEvent;
import com.hyeokjin.devflow.codeagent.git.GitCloneResult;
import com.hyeokjin.devflow.codeagent.git.GitCloneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DevTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(DevTaskConsumer.class);

    private final GitCloneService gitCloneService;

    public DevTaskConsumer(GitCloneService gitCloneService) {
        this.gitCloneService = gitCloneService;
    }

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

        log.info("[GIT CLONE STARTED] taskId={}, repository={}", event.taskId(), event.repository());

        try {
            GitCloneResult result = gitCloneService.cloneRepository(
                    event.taskId(),
                    event.repository()
            );

            log.info(
                    """
                    
                    ============================================================
                    [GIT CLONE COMPLETED]
                    Task ID   : {}
                    Workspace : {}
                    Branch    : {}
                    Files     : {}
                    Reused    : {}
                    ============================================================
                    """,
                    event.taskId(),
                    result.workspace(),
                    result.branch(),
                    result.fileCount(),
                    result.reused()
            );
        } catch (RuntimeException e) {
            log.error(
                    "[GIT CLONE FAILED] taskId={}, repository={}, reason={}",
                    event.taskId(),
                    event.repository(),
                    e.getMessage(),
                    e
            );
            throw e;
        }
    }
}
