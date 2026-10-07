package com.hyeokjin.devflow.codeagent.consumer;

import com.hyeokjin.devflow.codeagent.analysis.ProjectAnalysisResult;
import com.hyeokjin.devflow.codeagent.analysis.ProjectAnalysisService;
import com.hyeokjin.devflow.codeagent.event.CodeModifiedEventPublisher;
import com.hyeokjin.devflow.codeagent.event.DevTaskCreatedEvent;
import com.hyeokjin.devflow.codeagent.git.GitCloneResult;
import com.hyeokjin.devflow.codeagent.git.GitCloneService;
import com.hyeokjin.devflow.codeagent.modification.CodeModificationResult;
import com.hyeokjin.devflow.codeagent.modification.CodeModificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DevTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(DevTaskConsumer.class);

    private final GitCloneService gitCloneService;
    private final ProjectAnalysisService projectAnalysisService;
    private final CodeModificationService codeModificationService;
    private final CodeModifiedEventPublisher codeModifiedEventPublisher;

    public DevTaskConsumer(
            GitCloneService gitCloneService,
            ProjectAnalysisService projectAnalysisService,
            CodeModificationService codeModificationService,
            CodeModifiedEventPublisher codeModifiedEventPublisher
    ) {
        this.gitCloneService = gitCloneService;
        this.projectAnalysisService = projectAnalysisService;
        this.codeModificationService = codeModificationService;
        this.codeModifiedEventPublisher = codeModifiedEventPublisher;
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

        GitCloneResult result;

        try {
            result = gitCloneService.cloneRepository(event.taskId(), event.repository());

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

        log.info("[PROJECT ANALYSIS STARTED] taskId={}", event.taskId());

        ProjectAnalysisResult analysis;

        try {
            analysis = projectAnalysisService.analyze(
                    event.taskId(),
                    event.repository(),
                    event.prompt(),
                    result.workspace()
            );

            log.info(
                    """
                    
                    ============================================================
                    [PROJECT ANALYSIS COMPLETED]
                    Task ID          : {}
                    Provider         : {}
                    Model            : {}
                    Mock             : {}
                    Input Tokens     : {} (estimated)
                    Output Tokens    : {} (estimated)
                    Repo Map Files   : {}
                    Files Sent To AI : {}
                    Repo Map         : {}
                    Selected Files   : {}
                    Result File      : {}
                    ============================================================
                    """,
                    event.taskId(),
                    analysis.provider(),
                    analysis.model(),
                    analysis.mock(),
                    analysis.inputTokens(),
                    analysis.outputTokens(),
                    analysis.repoMapFiles(),
                    analysis.filesAnalyzed(),
                    analysis.repoMapFile(),
                    analysis.selectedFiles(),
                    analysis.resultFile()
            );
        } catch (RuntimeException e) {
            log.error(
                    "[PROJECT ANALYSIS FAILED] taskId={}, repository={}, reason={}",
                    event.taskId(),
                    event.repository(),
                    e.getMessage(),
                    e
            );
            throw e;
        }

        log.info("[CODE MODIFICATION STARTED] taskId={}", event.taskId());

        CodeModificationResult modification;

        try {
            modification = codeModificationService.modify(
                    event.taskId(),
                    event.repository(),
                    event.prompt(),
                    result.workspace(),
                    analysis.selectedFiles()
            );

            log.info(
                    """
                    
                    ============================================================
                    [CODE MODIFICATION COMPLETED]
                    Task ID       : {}
                    Local Branch  : {}
                    Provider      : {}
                    Model         : {}
                    Mock          : {}
                    Changed Files : {}
                    Plan File     : {}
                    Diff File     : {}
                    ============================================================
                    """,
                    event.taskId(),
                    modification.branchName(),
                    modification.provider(),
                    modification.model(),
                    modification.mock(),
                    modification.changedFiles(),
                    modification.planFile(),
                    modification.diffFile()
            );
        } catch (RuntimeException e) {
            log.error(
                    "[CODE MODIFICATION FAILED] taskId={}, repository={}, reason={}",
                    event.taskId(),
                    event.repository(),
                    e.getMessage(),
                    e
            );
            throw e;
        }

        codeModifiedEventPublisher.publish(
                event.taskId(),
                event.repository(),
                event.prompt(),
                result.workspace(),
                modification.branchName(),
                modification.changedFiles()
        );

        log.info(
                "[CODE_MODIFIED EVENT PUBLISHED] taskId={}, branch={}, changedFiles={}",
                event.taskId(),
                modification.branchName(),
                modification.changedFiles().size()
        );
    }
}
