package com.codeinspector.controller;

import com.codeinspector.common.Result;
import com.codeinspector.model.dto.StartReviewDTO;
import com.codeinspector.model.entity.ReviewIssue;
import com.codeinspector.model.entity.ReviewReport;
import com.codeinspector.model.entity.ReviewTask;
import com.codeinspector.model.entity.User;
import com.codeinspector.model.vo.IssueStatsVO;
import com.codeinspector.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 启动项目审查
     * 可通过请求体 styleEnabled=true 开启"按用户代码风格给出审查建议"
     */
    @PostMapping("/projects/{projectId}/start")
    public Result<Map<String, Object>> startReview(@PathVariable Long projectId,
                                                    @RequestBody(required = false) StartReviewDTO dto,
                                                    @AuthenticationPrincipal User user) {
        boolean styleEnabled = dto != null && Boolean.TRUE.equals(dto.getStyleEnabled());
        reviewService.startReview(projectId, user.getId(), styleEnabled);
        String msg = styleEnabled ? "审查任务已启动（按你的代码风格审查），请等待处理完成"
                : "审查任务已启动，请等待处理完成";
        return Result.success(msg, null);
    }

    /**
     * 手动分析项目代码风格画像
     */
    @PostMapping("/projects/{projectId}/analyze-style")
    public Result<Map<String, String>> analyzeStyle(@PathVariable Long projectId) {
        String profile = reviewService.analyzeStyle(projectId);
        return Result.success("风格画像已生成", Map.of("styleProfile", profile != null ? profile : ""));
    }

    /**
     * 获取项目当前的代码风格画像
     */
    @GetMapping("/projects/{projectId}/style")
    public Result<Map<String, String>> getStyle(@PathVariable Long projectId) {
        String profile = reviewService.getStyleProfile(projectId);
        return Result.success(Map.of("styleProfile", profile != null ? profile : ""));
    }

    /**
     * 获取审查进度
     */
    @GetMapping("/projects/{projectId}/progress")
    public Result<Map<String, Object>> getProgress(@PathVariable Long projectId) {
        return Result.success(reviewService.getReviewProgress(projectId));
    }

    /**
     * 获取项目所有问题
     */
    @GetMapping("/projects/{projectId}/issues")
    public Result<List<ReviewIssue>> getIssues(@PathVariable Long projectId) {
        return Result.success(reviewService.getProjectIssues(projectId));
    }

    /**
     * 获取文件级别问题（Monaco Editor标记用）
     */
    @GetMapping("/projects/{projectId}/files/{filePath}/issues")
    public Result<List<ReviewIssue>> getFileIssues(@PathVariable Long projectId,
                                                    @PathVariable String filePath) {
        // URL解码文件路径
        String decodedPath = java.net.URLDecoder.decode(filePath, java.nio.charset.StandardCharsets.UTF_8);
        return Result.success(reviewService.getFileIssues(projectId, decodedPath));
    }

    /**
     * 获取审查统计
     */
    @GetMapping("/projects/{projectId}/stats")
    public Result<IssueStatsVO> getStats(@PathVariable Long projectId) {
        return Result.success(reviewService.getIssueStats(projectId));
    }

    /**
     * 获取审查报告
     */
    @GetMapping("/projects/{projectId}/report")
    public Result<ReviewReport> getReport(@PathVariable Long projectId) {
        return Result.success(reviewService.getReport(projectId));
    }
}
