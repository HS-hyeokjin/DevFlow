package com.hyeokjin.devflow.reviewagent.review;

public interface ReviewClient {
    ReviewDecision review(ReviewRequest request);
}
