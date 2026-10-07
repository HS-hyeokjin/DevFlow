package com.hyeokjin.devflow.reviewagent.review;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MockReviewClientTest {

    private final MockReviewClient client = new MockReviewClient();

    @Test
    void approvesTestedNonEmptyDiff() {
        ReviewDecision decision = client.review(new ReviewRequest(
                "owner/repo",
                "fix",
                List.of("src/App.java"),
                "diff --git a/src/App.java b/src/App.java\n+// changed",
                "mvn verify",
                0
        ));

        assertTrue(decision.approved());
        assertTrue(decision.mock());
    }

    @Test
    void rejectsEmptyDiff() {
        ReviewDecision decision = client.review(new ReviewRequest(
                "owner/repo",
                "fix",
                List.of("src/App.java"),
                "",
                "mvn verify",
                0
        ));

        assertFalse(decision.approved());
    }
}
