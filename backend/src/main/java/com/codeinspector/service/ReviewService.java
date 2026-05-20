package com.codeinspector.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.codeinspector.common.BusinessException;
import com.codeinspector.model.entity.*;
import com.codeinspector.mapper.*;
import com.codeinspector.mq.producer.ReviewTaskProducer;
import com.codeinspector.model.vo.IssueStatsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 审查服务 - 核心业务逻辑
 * 负责审查任务的创建、调度、结果处理
 * 支持RabbitMQ模式和@Async线程池模式（降级）
 */
@Slf4j
@Service
public class ReviewService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewIssueMapper reviewIssueMapper;
    private final ReviewReportMapper reviewReportMapper;
    private final ProjectMapper projectMapper;
    private final CodeChunkMapper codeChunkMapper;
    private final CodeFileMapper codeFileMapper;
    private final CodeAnalysisService codeAnalysisService;
    private final AIService aiService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private ReviewTaskProducer reviewTaskProducer;

    private final ReviewTaskExecutor reviewTaskExecutor;

    public ReviewService(ReviewTaskMapper reviewTaskMapper, ReviewIssueMapper reviewIssueMapper,
                         ReviewReportMapper reviewReportMapper, ProjectMapper projectMapper,
                         CodeChunkMapper codeChunkMapper, CodeFileMapper codeFileMapper,
                         CodeAnalysisService codeAnalysisService, AIService aiService,
                         RedisTemplate<String, Object> redisTemplate,
                         @org.springframework.context.annotation.Lazy ReviewTaskExecutor reviewTaskExecutor) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewIssueMapper = reviewIssueMapper;
        this.reviewReportMapper = reviewReportMapper;
        this.projectMapper = projectMapper;
        this.codeChunkMapper = codeChunkMapper;
        this.codeFileMapper = codeFileMapper;
        this.codeAnalysisService = codeAnalysisService;
        this.aiService = aiService;
        this.redisTemplate = redisTemplate;
        this.reviewTaskExecutor = reviewTaskExecutor;
    }

    private static final String REVIEW_PROGRESS_KEY = "review:progress:";
    private static final String REVIEW_LOCK_KEY = "review:lock:project:";

    /**
     * 启动项目审查 - 创建所有切片审查任务并发送到RabbitMQ
     */
    @Transactional
    public void startReview(Long projectId, Long userId) {
        // Redis锁防止重复审查
        String lockKey = REVIEW_LOCK_KEY + projectId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, userId, 30, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("该项目正在审查中，请稍后再试");
        }

        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }

        // 更新项目状态
        project.setReviewStatus("IN_PROGRESS");
        projectMapper.updateById(project);

        // 获取所有切片
        List<CodeChunk> chunks = codeChunkMapper.findByProjectId(projectId);
        if (chunks.isEmpty()) {
            throw new BusinessException("该项目没有待审查的代码切片，请先上传代码");
        }

        // 清空旧审查结果
        clearOldReviewResults(projectId);

        // 创建审查任务
        boolean useMq = reviewTaskProducer != null;
        for (CodeChunk chunk : chunks) {
            ReviewTask task = new ReviewTask();
            task.setProjectId(projectId);
            task.setChunkId(chunk.getId());
            task.setUserId(userId);
            task.setStatus("PENDING");
            task.setCreateTime(LocalDateTime.now());
            reviewTaskMapper.insert(task);

            if (useMq) {
                // RabbitMQ模式 - 发送到消息队列异步处理
                reviewTaskProducer.sendReviewTask(task);
            } else {
                // 降级模式 - 使用@Async线程池异步处理
                reviewTaskExecutor.executeAsync(task);
            }
        }

        int totalChunks = chunks.size();
        log.info("项目[{}]审查任务已创建，共{}个切片，模式: {}", projectId, totalChunks,
                useMq ? "RabbitMQ" : "@Async线程池");
    }

    /**
     * 降级模式 - 使用@Async异步处理（无需RabbitMQ）
     */
    /**
     * 处理单个切片审查（由MQ消费者或@Async线程池调用）
     */
    @Transactional
    public void processChunkReview(ReviewTask task) {
        try {
            task.setStatus("PROCESSING");
            task.setStartTime(LocalDateTime.now());
            reviewTaskMapper.updateById(task);

            // 获取切片内容
            CodeChunk chunk = codeChunkMapper.selectById(task.getChunkId());
            if (chunk == null) {
                throw new BusinessException("审查切片不存在");
            }

            // 获取文件信息
            CodeFile codeFile = codeFileMapper.selectById(chunk.getFileId());
            String fileName = codeFile != null ? codeFile.getFileName() : "unknown";

            // 调用AI审查
            JSONObject aiResult = aiService.reviewCodeChunk(
                    chunk.getChunkContent(),
                    chunk.getElementName(),
                    chunk.getChunkType(),
                    fileName
            );

            // 解析审查结果
            JSONArray issues = aiResult.getJSONArray("issues");
            if (issues != null && !issues.isEmpty()) {
                for (int i = 0; i < issues.size(); i++) {
                    JSONObject issueJson = issues.getJSONObject(i);
                    saveIssue(task, chunk, codeFile, issueJson);
                }
            }

            // 更新任务状态
            task.setStatus("COMPLETED");
            task.setFinishTime(LocalDateTime.now());
            reviewTaskMapper.updateById(task);

            // 事务提交后再检查是否全部完成，避免读取到未提交状态
            org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        checkAndGenerateReport(task.getProjectId());
                    }
                });

        } catch (Exception e) {
            log.error("审查切片[{}]失败: ", task.getId(), e);
            task.setStatus("FAILED");
            task.setErrorMsg(e.getMessage());
            task.setFinishTime(LocalDateTime.now());
            reviewTaskMapper.updateById(task);
        }
    }

    /**
     * 保存审查发现的问题
     */
    private void saveIssue(ReviewTask task, CodeChunk chunk, CodeFile codeFile, JSONObject issueJson) {
        ReviewIssue issue = new ReviewIssue();
        issue.setTaskId(task.getId());
        issue.setProjectId(task.getProjectId());

        String filePath = codeFile != null ? codeFile.getFilePath() : "unknown";
        issue.setFilePath(filePath);

        // 行号映射：切片内的相对行号 -> 文件绝对行号
        int relativeStartLine = issueJson.getIntValue("lineStart", 1);
        int relativeEndLine = issueJson.getIntValue("lineEnd", relativeStartLine);
        int chunkStartLine = chunk.getStartLine() != null ? chunk.getStartLine() : 1;
        issue.setLineStart(chunkStartLine + relativeStartLine - 1);
        issue.setLineEnd(chunkStartLine + relativeEndLine - 1);

        issue.setSeverity(issueJson.getString("severity") != null
                ? issueJson.getString("severity").toUpperCase() : "MINOR");
        issue.setCategory(issueJson.getString("category") != null
                ? issueJson.getString("category").toUpperCase() : "CODE_STYLE");
        issue.setTitle(issueJson.getString("title"));
        issue.setDescription(issueJson.getString("description"));
        issue.setSuggestion(issueJson.getString("suggestion"));
        issue.setFixedCode(issueJson.getString("fixedCode"));

        // 提取问题代码片段
        if (chunk.getChunkContent() != null) {
            String[] lines = chunk.getChunkContent().split("\n");
            int startIdx = Math.max(0, relativeStartLine - 1);
            int endIdx = Math.min(lines.length, relativeEndLine);
            StringBuilder snippet = new StringBuilder();
            for (int i = startIdx; i < endIdx; i++) {
                snippet.append(lines[i]).append("\n");
            }
            issue.setCodeSnippet(snippet.toString().trim());
        }

        issue.setStatus("OPEN");
        issue.setCreateTime(LocalDateTime.now());
        reviewIssueMapper.insert(issue);
    }

    /**
     * 检查并生成审查报告
     */
    private void checkAndGenerateReport(Long projectId) {
        // 从数据库直接统计任务状态（比Redis更可靠）
        List<ReviewTask> allTasks = reviewTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReviewTask>()
                        .eq(ReviewTask::getProjectId, projectId));
        long total = allTasks.size();
        long completed = allTasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long failed = allTasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count();

        log.info("项目[{}]审查进度: {}/{} 完成, {} 失败", projectId, completed, total, failed);

        if (total > 0 && (completed + failed) >= total) {
            generateReport(projectId);
            redisTemplate.delete(REVIEW_LOCK_KEY + projectId);
            redisTemplate.delete(REVIEW_PROGRESS_KEY + projectId);
            log.info("项目[{}]审查全部完成，报告已生成", projectId);
        }
    }

    /**
     * 生成审查报告 - 统计数据
     */
    @Transactional
    public void generateReport(Long projectId) {
        List<ReviewIssue> issues = reviewIssueMapper.findByProjectId(projectId);
        List<Map<String, Object>> severityStats = reviewIssueMapper.countBySeverity(projectId);
        List<Map<String, Object>> categoryStats = reviewIssueMapper.countByCategory(projectId);

        Map<String, Long> severityMap = new HashMap<>();
        for (Map<String, Object> s : severityStats) {
            severityMap.put((String) s.get("severity"),
                    ((Number) s.get("cnt")).longValue());
        }

        Map<String, Long> categoryMap = new HashMap<>();
        for (Map<String, Object> c : categoryStats) {
            categoryMap.put((String) c.get("category"),
                    ((Number) c.get("cnt")).longValue());
        }

        Project project = projectMapper.selectById(projectId);
        long totalLines = project.getTotalLines() != null ? project.getTotalLines() : 1;

        ReviewReport report = reviewReportMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReviewReport>()
                        .eq(ReviewReport::getProjectId, projectId));
        if (report == null) {
            report = new ReviewReport();
            report.setProjectId(projectId);
        }

        report.setTotalIssues(issues.size());
        report.setCriticalCount(severityMap.getOrDefault("CRITICAL", 0L).intValue());
        report.setMajorCount(severityMap.getOrDefault("MAJOR", 0L).intValue());
        report.setMinorCount(severityMap.getOrDefault("MINOR", 0L).intValue());
        report.setInfoCount(severityMap.getOrDefault("INFO", 0L).intValue());
        report.setSecurityCount(categoryMap.getOrDefault("SECURITY", 0L).intValue());
        report.setBugCount(categoryMap.getOrDefault("BUG", 0L).intValue());
        report.setStyleCount(categoryMap.getOrDefault("CODE_STYLE", 0L).intValue());
        report.setPerformanceCount(categoryMap.getOrDefault("PERFORMANCE", 0L).intValue());
        report.setBestPracticeCount(categoryMap.getOrDefault("BEST_PRACTICE", 0L).intValue());

        // Bug率 = (严重 + 重要) / 总行数 * 1000
        long bugLines = severityMap.getOrDefault("CRITICAL", 0L) + severityMap.getOrDefault("MAJOR", 0L);
        report.setBugRate(BigDecimal.valueOf(bugLines * 1000.0 / totalLines)
                .setScale(4, RoundingMode.HALF_UP));

        report.setReviewedFiles(codeFileMapper.countByProjectId(projectId).intValue());
        report.setReviewedLines(totalLines);

        // 综合评分
        int score = calculateScore(issues, totalLines);
        report.setScore(score);

        // 生成总结
        report.setSummary(generateSummary(report));

        report.setUpdateTime(LocalDateTime.now());
        if (report.getId() != null) {
            reviewReportMapper.updateById(report);
        } else {
            reviewReportMapper.insert(report);
        }

        // 更新项目状态
        project.setReviewStatus("COMPLETED");
        projectMapper.updateById(project);
    }

    /**
     * 综合评分算法
     */
    private int calculateScore(List<ReviewIssue> issues, long totalLines) {
        if (issues.isEmpty()) return 100;

        double penalty = 0;
        for (ReviewIssue issue : issues) {
            switch (issue.getSeverity()) {
                case "CRITICAL" -> penalty += 5;
                case "MAJOR" -> penalty += 3;
                case "MINOR" -> penalty += 1;
                case "INFO" -> penalty += 0.3;
            }
        }

        double densityPenalty = (penalty * 1000.0) / totalLines;
        return Math.max(0, (int) Math.round(100 - densityPenalty));
    }

    private String generateSummary(ReviewReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("本次审查共发现").append(report.getTotalIssues()).append("个问题，");
        if (report.getCriticalCount() > 0) {
            sb.append("其中严重问题").append(report.getCriticalCount()).append("个，需立即修复；");
        }
        if (report.getBugCount() > 0) {
            sb.append("潜在Bug").append(report.getBugCount()).append("个；");
        }
        sb.append("综合评分").append(report.getScore()).append("分。");
        return sb.toString();
    }

    /**
     * 获取审查进度
     */
    public Map<String, Object> getReviewProgress(Long projectId) {
        Map<String, Object> progress = new HashMap<>();
        List<ReviewTask> allTasks = reviewTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReviewTask>()
                        .eq(ReviewTask::getProjectId, projectId));
        long total = allTasks.size();
        long completed = allTasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long failed = allTasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count();
        progress.put("total", total);
        progress.put("completed", completed + failed);
        progress.put("percentage", total == 0 ? 0 : (int)((completed + failed) * 100 / total));
        return progress;
    }

    /**
     * 获取审查问题列表
     */
    public List<ReviewIssue> getProjectIssues(Long projectId) {
        return reviewIssueMapper.findByProjectId(projectId);
    }

    /**
     * 获取审查问题统计
     */
    public IssueStatsVO getIssueStats(Long projectId) {
        List<ReviewIssue> issues = reviewIssueMapper.findByProjectId(projectId);
        List<Map<String, Object>> severityData = reviewIssueMapper.countBySeverity(projectId);
        List<Map<String, Object>> categoryData = reviewIssueMapper.countByCategory(projectId);

        IssueStatsVO stats = new IssueStatsVO();
        stats.setTotalIssues(issues.size());
        stats.setSeverityStats(severityData.stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("severity"),
                        m -> ((Number) m.get("cnt")).longValue())));
        stats.setCategoryStats(categoryData.stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("category"),
                        m -> ((Number) m.get("cnt")).longValue())));
        return stats;
    }

    /**
     * 根据文件获取问题（用于Monaco Editor标记）
     */
    public List<ReviewIssue> getFileIssues(Long projectId, String filePath) {
        return reviewIssueMapper.findByFile(projectId, filePath);
    }

    /**
     * 获取审查报告
     */
    public ReviewReport getReport(Long projectId) {
        return reviewReportMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReviewReport>()
                        .eq(ReviewReport::getProjectId, projectId));
    }

    private void clearOldReviewResults(Long projectId) {
        // 删除旧问题
        List<ReviewIssue> oldIssues = reviewIssueMapper.findByProjectId(projectId);
        for (ReviewIssue issue : oldIssues) {
            reviewIssueMapper.deleteById(issue.getId());
        }
        // 删除旧报告
        ReviewReport oldReport = getReport(projectId);
        if (oldReport != null) {
            reviewReportMapper.deleteById(oldReport.getId());
        }
    }
}
