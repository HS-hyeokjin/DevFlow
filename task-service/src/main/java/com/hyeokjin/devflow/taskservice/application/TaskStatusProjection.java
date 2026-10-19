package com.hyeokjin.devflow.taskservice.application;

import com.hyeokjin.devflow.taskservice.event.CodeModifiedEvent;
import com.hyeokjin.devflow.taskservice.event.MergeResultEvent;
import com.hyeokjin.devflow.taskservice.event.ReviewResultEvent;
import com.hyeokjin.devflow.taskservice.event.TestResultEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TaskStatusProjection {

    private final TaskStateStore store;

    public TaskStatusProjection(TaskStateStore store) {
        this.store = store;
    }

    @KafkaListener(
            topics = "${devflow.kafka.topics.code-modified}",
            containerFactory = "codeModifiedKafkaListenerContainerFactory"
    )
    public void onCodeModified(CodeModifiedEvent event) {
        updateIfPresent(
                event.taskId(),
                "TESTING",
                event.branch(),
                null,
                null,
                event.createdAt()
        );
    }

    @KafkaListener(
            topics = "${devflow.kafka.topics.test-result}",
            containerFactory = "testResultKafkaListenerContainerFactory"
    )
    public void onTestResult(TestResultEvent event) {
        String status = switch (event.status()) {
            case "TEST_FAILED" -> "FIXING";
            case "TEST_PASSED" -> "REVIEWING";
            default -> null;
        };

        if (status != null) {
            updateIfPresent(
                    event.taskId(),
                    status,
                    event.branch(),
                    null,
                    null,
                    event.createdAt()
            );
        }
    }

    @KafkaListener(
            topics = "${devflow.kafka.topics.review-result}",
            containerFactory = "reviewResultKafkaListenerContainerFactory"
    )
    public void onReviewResult(ReviewResultEvent event) {
        String status = switch (event.eventType()) {
            case "PULL_REQUEST_CREATED" -> "WAITING_APPROVAL";
            case "REVIEW_REJECTED" -> "REVIEW_REJECTED";
            case "REVIEW_APPROVED" -> "REVIEW_APPROVED_LOCAL_ONLY";
            default -> null;
        };

        if (status != null) {
            updateIfPresent(
                    event.taskId(),
                    status,
                    event.branch(),
                    event.pullRequestNumber(),
                    event.pullRequestUrl(),
                    event.createdAt()
            );
        }
    }

    @KafkaListener(
            topics = "${devflow.kafka.topics.merge-result}",
            containerFactory = "mergeResultKafkaListenerContainerFactory"
    )
    public void onMergeResult(MergeResultEvent event) {
        String status = switch (event.eventType()) {
            case "PULL_REQUEST_MERGED" -> "COMPLETED";
            case "HUMAN_REJECTED" -> "REJECTED";
            case "MERGE_FAILED" -> "MERGE_FAILED";
            default -> null;
        };

        if (status != null) {
            updateIfPresent(
                    event.taskId(),
                    status,
                    event.branch(),
                    event.pullRequestNumber(),
                    event.pullRequestUrl(),
                    event.createdAt()
            );
        }
    }

    private void updateIfPresent(
            java.util.UUID taskId,
            String status,
            String branch,
            Integer pullRequestNumber,
            String pullRequestUrl,
            java.time.Instant eventTime
    ) {
        if (store.find(taskId).isEmpty()) {
            return;
        }

        store.update(
                taskId,
                status,
                branch,
                pullRequestNumber,
                pullRequestUrl,
                eventTime
        );
    }
}
