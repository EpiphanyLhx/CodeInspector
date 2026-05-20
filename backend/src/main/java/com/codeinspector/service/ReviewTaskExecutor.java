package com.codeinspector.service;

import com.codeinspector.model.entity.ReviewTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewTaskExecutor {

    private final ReviewService reviewService;

    @Async("taskExecutor")
    public void executeAsync(ReviewTask task) {
        log.info("异步审查任务[{}]", task.getId());
        reviewService.processChunkReview(task);
    }
}
