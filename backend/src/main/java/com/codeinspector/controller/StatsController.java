package com.codeinspector.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeinspector.common.Result;
import com.codeinspector.mapper.ReviewIssueMapper;
import com.codeinspector.mapper.ReviewReportMapper;
import com.codeinspector.mapper.ProjectMapper;
import com.codeinspector.model.entity.ReviewIssue;
import com.codeinspector.model.entity.ReviewReport;
import com.codeinspector.model.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计控制器 - 提供ECharts图表所需数据
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ReviewIssueMapper reviewIssueMapper;
    private final ReviewReportMapper reviewReportMapper;
    private final ProjectMapper projectMapper;

    /**
     * 获取仪表盘统计数据
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = new HashMap<>();

        // 项目总数
        data.put("totalProjects", projectMapper.selectCount(null));

        // 已完成审查数
        data.put("reviewedProjects", projectMapper.selectCount(
                new LambdaQueryWrapper<Project>().eq(Project::getReviewStatus, "COMPLETED")));

        // 总问题数
        List<ReviewIssue> allIssues = reviewIssueMapper.selectList(null);
        data.put("totalIssues", allIssues.size());

        // 待解决问题数
        data.put("openIssues", allIssues.stream()
                .filter(i -> "OPEN".equals(i.getStatus())).count());

        // 严重程度分布
        Map<String, Long> severityDistribution = allIssues.stream()
                .collect(Collectors.groupingBy(ReviewIssue::getSeverity, Collectors.counting()));
        data.put("severityDistribution", severityDistribution);

        // 分类分布
        Map<String, Long> categoryDistribution = allIssues.stream()
                .collect(Collectors.groupingBy(ReviewIssue::getCategory, Collectors.counting()));
        data.put("categoryDistribution", categoryDistribution);

        // 平均评分
        List<ReviewReport> reports = reviewReportMapper.selectList(null);
        double avgScore = reports.stream()
                .filter(r -> r.getScore() != null)
                .mapToInt(ReviewReport::getScore)
                .average().orElse(0);
        data.put("averageScore", Math.round(avgScore * 100.0) / 100.0);

        return Result.success(data);
    }

    /**
     * 获取Bug率趋势数据（用于折线图）
     */
    @GetMapping("/bug-rate-trend")
    public Result<List<Map<String, Object>>> bugRateTrend() {
        List<ReviewReport> reports = reviewReportMapper.selectList(
                new LambdaQueryWrapper<ReviewReport>()
                        .orderByDesc(ReviewReport::getCreateTime)
                        .last("LIMIT 20"));

        return Result.success(reports.stream()
                .sorted(Comparator.comparing(ReviewReport::getCreateTime))
                .map(r -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("projectId", r.getProjectId());
                    point.put("bugRate", r.getBugRate());
                    point.put("score", r.getScore());
                    point.put("date", r.getCreateTime().toLocalDate().toString());
                    return point;
                })
                .collect(Collectors.toList()));
    }
}
