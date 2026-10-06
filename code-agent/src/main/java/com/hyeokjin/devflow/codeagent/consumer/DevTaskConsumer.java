package com.hyeokjin.devflow.codeagent.consumer;

import com.hyeokjin.devflow.codeagent.event.DevTaskCreatedEvent;
import com.hyeokjin.devflow.codeagent.analysis.ProjectAnalysisResult;
import com.hyeokjin.devflow.codeagent.analysis.ProjectAnalysisService;
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
    private final ProjectAnalysisService projectAnalysisService;

    public DevTaskConsumer(
            GitCloneService gitCloneService,
            ProjectAnalysisService projectAnalysisService
    ) {
        this.gitCloneService = gitCloneService;
        this.projectAnalysisService = projectAnalysisService;
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

            log.info("[PROJECT ANALYSIS STARTED] taskId={}", event.taskId());

            ProjectAnalysisResult analysis = projectAnalysisService.analyze(
                    event.taskId(),
                    event.repository(),
                    event.prompt(),
                    result.workspace()
            );

            log.info(
                    """
                    
                    ============================================================
                    [PROJECT ANALYSIS COMPLETED]
                    Task ID       : {}
                    Model         : {}
                    Files Analyzed: {}
                    Result File   : {}
                    
                    {}
                    ============================================================
                    """,
                    event.taskId(),
                    analysis.model(),
                    analysis.filesAnalyzed(),
                    analysis.resultFile(),
                    analysis.analysis()
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
