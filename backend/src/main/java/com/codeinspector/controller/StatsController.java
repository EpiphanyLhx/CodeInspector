package com.codeinspector.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeinspector.common.Result;
import com.codeinspector.mapper.ReviewIssueMapper;
import com.codeinspector.mapper.ReviewReportMapper;
import com.codeinspector.mapper.ProjectMapper;
import com.codeinspector.model.entity.ReviewIssue;
import com.codeinspector.model.entity.ReviewReport;
import com.codeinspector.model.entity.Project;
import com.codeinspector.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计控制器 - 提供ECharts图表所需数据
 * 统计范围：当前登录用户自己上传（创建）的全部代码；
 * 默认视图由前端定位到该用户最新上传且已完成审查的项目。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ReviewIssueMapper reviewIssueMapper;
    private final ReviewReportMapper reviewReportMapper;
    private final ProjectMapper projectMapper;

    /**
     * 获取仪表盘统计数据（仅统计当前用户自己上传的代码）
     * 同时返回 latestProjectId：当前用户最新上传且已完成审查的项目ID，供前端默认选中
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(@AuthenticationPrincipal User user) {
        Map<String, Object> data = new HashMap<>();
        Long userId = user.getId();

        // 当前用户自己创建的全部项目
        List<Project> userProjects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getCreatorId, userId));
        List<Long> projectIds = userProjects.stream()
                .map(Project::getId).collect(Collectors.toList());

        // 项目总数
        data.put("totalProjects", userProjects.size());

        // 已完成审查数
        long reviewedCount = userProjects.stream()
                .filter(p -> "COMPLETED".equals(p.getReviewStatus())).count();
        data.put("reviewedProjects", reviewedCount);

        // 当前用户所有项目下的问题
        List<ReviewIssue> allIssues = projectIds.isEmpty()
                ? Collections.emptyList()
                : reviewIssueMapper.selectList(
                        new LambdaQueryWrapper<ReviewIssue>()
                                .in(ReviewIssue::getProjectId, projectIds));

        // 总问题数
        data.put("totalIssues", allIssues.size());

        // 待解决问题数
        data.put("openIssues", allIssues.stream()
                .filter(i -> "OPEN".equals(i.getStatus())).count());

        // 严重程度分布 (确保所有级别都出现，即使数量为0)
        Map<String, Long> severityDistribution = new LinkedHashMap<>();
        severityDistribution.put("CRITICAL", 0L);
        severityDistribution.put("MAJOR", 0L);
        severityDistribution.put("MINOR", 0L);
        severityDistribution.put("INFO", 0L);
        allIssues.stream()
                .collect(Collectors.groupingBy(ReviewIssue::getSeverity, Collectors.counting()))
                .forEach(severityDistribution::put);
        data.put("severityDistribution", severityDistribution);

        // 分类分布 (确保所有分类都出现，即使数量为0)
        Map<String, Long> categoryDistribution = new LinkedHashMap<>();
        categoryDistribution.put("SECURITY", 0L);
        categoryDistribution.put("BUG", 0L);
        categoryDistribution.put("CODE_STYLE", 0L);
        categoryDistribution.put("PERFORMANCE", 0L);
        categoryDistribution.put("BEST_PRACTICE", 0L);
        allIssues.stream()
                .collect(Collectors.groupingBy(ReviewIssue::getCategory, Collectors.counting()))
                .forEach(categoryDistribution::put);
        data.put("categoryDistribution", categoryDistribution);

        // 当前用户最新上传且已完成审查的项目ID（供前端默认选中）
        Project latestCompleted = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getCreatorId, userId)
                .eq(Project::getReviewStatus, "COMPLETED")
                .orderByDesc(Project::getCreateTime)
                .last("LIMIT 1"));
        data.put("latestProjectId", latestCompleted != null ? latestCompleted.getId() : null);

        return Result.success(data);
    }

    /**
     * 获取Bug率趋势数据（仅当前用户自己上传的代码，用于折线图）
     */
    @GetMapping("/bug-rate-trend")
    public Result<List<Map<String, Object>>> bugRateTrend(@AuthenticationPrincipal User user) {
        Long userId = user.getId();

        List<Project> userProjects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getCreatorId, userId));
        List<Long> projectIds = userProjects.stream()
                .map(Project::getId).collect(Collectors.toList());

        if (projectIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<ReviewReport> reports = reviewReportMapper.selectList(
                new LambdaQueryWrapper<ReviewReport>()
                        .in(ReviewReport::getProjectId, projectIds)
                        .orderByDesc(ReviewReport::getCreateTime)
                        .last("LIMIT 20"));

        return Result.success(reports.stream()
                .sorted(Comparator.comparing(ReviewReport::getCreateTime))
                .map(r -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("projectId", r.getProjectId());
                    point.put("bugRate", r.getBugRate());
                    point.put("date", r.getCreateTime().toLocalDate().toString());
                    return point;
                })
                .collect(Collectors.toList()));
    }
}
