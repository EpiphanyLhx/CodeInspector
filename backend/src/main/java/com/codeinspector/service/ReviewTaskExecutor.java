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
        // 事务已提交，在事务外部检查完成状态并生成报告
        try {
            reviewService.afterTaskComplete(task.getProjectId());
        } catch (Exception e) {
            log.error("检查项目[{}]完成状态失败: {}", task.getProjectId(), e.getMessage());
        }
    }
}
