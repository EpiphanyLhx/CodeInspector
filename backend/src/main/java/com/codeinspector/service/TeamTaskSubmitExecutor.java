package com.codeinspector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 团队审查任务异步提交执行器。
 * <p>提交接口先同步受理并立即返回，拉取代码/扫描/触发审查这些慢操作放到后台线程，
 * 避免 Git clone/pull 耗时长导致 HTTP 请求超时。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamTaskSubmitExecutor {

    private final TeamReviewTaskService teamReviewTaskService;

    @Async("teamTaskExecutor")
    public void submitAsync(Long taskId, Long userId) {
        log.info("后台开始执行团队审查任务[{}]的代码拉取与审查, 提交人={}", taskId, userId);
        teamReviewTaskService.executeSubmission(taskId, userId);
    }
}
