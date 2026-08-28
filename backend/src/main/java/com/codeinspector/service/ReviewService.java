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
    private final CodeStyleService codeStyleService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApiKeyService apiKeyService;

    @Autowired(required = false)
    private ReviewTaskProducer reviewTaskProducer;

    private final ReviewTaskExecutor reviewTaskExecutor;

    public ReviewService(ReviewTaskMapper reviewTaskMapper, ReviewIssueMapper reviewIssueMapper,
                         ReviewReportMapper reviewReportMapper, ProjectMapper projectMapper,
                         CodeChunkMapper codeChunkMapper, CodeFileMapper codeFileMapper,
                         CodeAnalysisService codeAnalysisService, AIService aiService,
                         CodeStyleService codeStyleService,
                         RedisTemplate<String, Object> redisTemplate,
                         ApiKeyService apiKeyService,
                         @org.springframework.context.annotation.Lazy ReviewTaskExecutor reviewTaskExecutor) {
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewIssueMapper = reviewIssueMapper;
        this.reviewReportMapper = reviewReportMapper;
        this.projectMapper = projectMapper;
        this.codeChunkMapper = codeChunkMapper;
        this.codeFileMapper = codeFileMapper;
        this.codeAnalysisService = codeAnalysisService;
        this.aiService = aiService;
        this.codeStyleService = codeStyleService;
        this.redisTemplate = redisTemplate;
        this.apiKeyService = apiKeyService;
        this.reviewTaskExecutor = reviewTaskExecutor;
    }

    private static final String REVIEW_PROGRESS_KEY = "review:progress:";
    private static final String REVIEW_LOCK_KEY = "review:lock:project:";

    private static final Set<String> VALID_SEVERITIES = Set.of("CRITICAL", "MAJOR", "MINOR", "INFO");
    private static final Set<String> VALID_CATEGORIES =
            Set.of("SECURITY", "BUG", "CODE_STYLE", "PERFORMANCE", "BEST_PRACTICE");

    /**
     * 启动项目审查 - 创建所有切片审查任务并发送到RabbitMQ
     * @param styleEnabled 是否按用户代码风格给出审查建议
     */
    @Transactional
    public void startReview(Long projectId, Long userId, boolean styleEnabled) {
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

        // 代码风格偏好：启用时若画像尚未生成则现场分析
        if (styleEnabled) {
            if (project.getStyleProfile() == null || project.getStyleProfile().isBlank()) {
                log.info("项目[{}]启用风格审查但画像为空，开始自动分析代码风格", projectId);
                codeStyleService.analyzeAndSaveStyle(projectId);
                // 重新读取，拿到分析后写入的画像
                project = projectMapper.selectById(projectId);
            }
            project.setStyleEnabled(1);
            log.info("项目[{}]已启用按用户代码风格审查", projectId);
        } else {
            project.setStyleEnabled(0);
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

            // 获取用户自定义API Key（如果配置了Active的）
            UserApiKey userKey = apiKeyService.getActiveDecryptedKey(task.getUserId());

            // 读取项目的代码风格画像（启用风格审查时注入 prompt）
            String styleProfile = null;
            Project project = projectMapper.selectById(task.getProjectId());
            if (project != null && project.getStyleEnabled() != null && project.getStyleEnabled() == 1
                    && project.getStyleProfile() != null && !project.getStyleProfile().isBlank()) {
                styleProfile = project.getStyleProfile();
            }

            // 记录使用的AI提供商和模型
            if (userKey != null) {
                task.setAiProvider(userKey.getProvider() + "(custom)");
                task.setAiModel(userKey.getModelName());
                if (userKey.getIsValid() == null || userKey.getIsValid() != 1) {
                    log.warn("审查切片[{}]使用用户自定义API(未验证状态): provider={}, model={}",
                            task.getId(), userKey.getProvider(), userKey.getModelName());
                } else {
                    log.debug("审查切片[{}]使用用户自定义API: provider={}, model={}",
                            task.getId(), userKey.getProvider(), userKey.getModelName());
                }
            }

            // 调用AI审查
            JSONObject aiResult = aiService.reviewCodeChunk(
                    chunk.getChunkContent(),
                    chunk.getElementName(),
                    chunk.getChunkType(),
                    fileName,
                    userKey,
                    styleProfile
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

        } catch (Exception e) {
            log.error("审查切片[{}]失败: ", task.getId(), e);
            task.setStatus("FAILED");
            task.setErrorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(200, e.getMessage().length())) : "未知错误");
            task.setFinishTime(LocalDateTime.now());
            reviewTaskMapper.updateById(task);
        }
    }

    /**
     * 任务完成后调用，检查是否全部完成并生成报告
     */
    public void afterTaskComplete(Long projectId) {
        checkAndGenerateReport(projectId);
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

        String severity = issueJson.getString("severity");
        if (severity != null) severity = severity.trim().toUpperCase();
        if (!VALID_SEVERITIES.contains(severity)) {
            log.warn("切片[{}]返回非法severity: {}, 回退为MINOR", task.getId(), severity);
            severity = "MINOR";
        }
        issue.setSeverity(severity);

        String category = issueJson.getString("category");
        if (category != null) category = category.trim().toUpperCase();
        if (!VALID_CATEGORIES.contains(category)) {
            log.warn("切片[{}]返回非法category: {}, 回退为BEST_PRACTICE", task.getId(), category);
            category = "BEST_PRACTICE";
        }
        issue.setCategory(category);

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
        // 使用Redis锁防止并发生成报告
        String genLockKey = "review:genlock:" + projectId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(genLockKey, 1, 120, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            log.info("项目[{}]报告正在生成中，跳过重复检测", projectId);
            return;
        }

        try {
            // 从数据库直接统计任务状态（比Redis更可靠）
            List<ReviewTask> allTasks = reviewTaskMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReviewTask>()
                            .eq(ReviewTask::getProjectId, projectId));
            long total = allTasks.size();
            long completed = allTasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
            long failed = allTasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count();

            log.info("项目[{}]审查进度: {}/{} 完成, {} 失败", projectId, completed, total, failed);

            if (total > 0 && (completed + failed) >= total) {
                // 全部失败则标记为失败状态
                if (completed == 0 && failed > 0) {
                    Project project = projectMapper.selectById(projectId);
                    if (project != null && !"COMPLETED".equals(project.getReviewStatus())) {
                        project.setReviewStatus("FAILED");
                        projectMapper.updateById(project);
                        log.warn("项目[{}]所有审查任务均失败，状态更新为FAILED", projectId);
                    }
                } else {
                    try {
                        generateReport(projectId);
                        log.info("项目[{}]审查全部完成，报告已生成", projectId);
                    } catch (Exception e) {
                        log.error("项目[{}]报告生成失败: ", projectId, e);
                        // 报告生成失败时标记项目为失败
                        Project project = projectMapper.selectById(projectId);
                        if (project != null && !"COMPLETED".equals(project.getReviewStatus())) {
                            project.setReviewStatus("FAILED");
                            projectMapper.updateById(project);
                        }
                    }
                }
                // 无论报告是否成功生成，都释放审查锁（所有任务已结束）
                redisTemplate.delete(REVIEW_LOCK_KEY + projectId);
                redisTemplate.delete(REVIEW_PROGRESS_KEY + projectId);
            }
        } finally {
            redisTemplate.delete(genLockKey);
        }
    }

    /**
     * 生成审查报告 - 统计数据
     */
    public void generateReport(Long projectId) {
        log.info("开始生成项目[{}]审查报告", projectId);
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

    private String generateSummary(ReviewReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("本次审查共发现").append(report.getTotalIssues()).append("个问题，");
        if (report.getCriticalCount() > 0) {
            sb.append("其中严重问题").append(report.getCriticalCount()).append("个，需立即修复；");
        }
        if (report.getBugCount() > 0) {
            sb.append("潜在Bug").append(report.getBugCount()).append("个。");
        }
        return sb.toString();
    }

    /**
     * 获取审查进度（自动修复卡住的项目）
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
        int percentage = total == 0 ? 0 : (int)((completed + failed) * 100 / total);
        progress.put("percentage", percentage);

        // 自动修复：任务全部完成但项目状态仍为IN_PROGRESS
        // 处理两种情况：1) 旧数据卡住 2) 异步报告生成尚未提交
        if (percentage >= 100 && total > 0) {
            Project project = projectMapper.selectById(projectId);
            if (project != null && "IN_PROGRESS".equals(project.getReviewStatus())) {
                log.info("检测到项目[{}]任务全部完成但状态卡住，自动生成报告", projectId);
                checkAndGenerateReport(projectId);

                // 如果报告正在由其他线程生成（锁被占用），等待其完成
                String genLockKey = "review:genlock:" + projectId;
                for (int retry = 0; retry < 10; retry++) {
                    project = projectMapper.selectById(projectId);
                    if (project != null && !"IN_PROGRESS".equals(project.getReviewStatus())) {
                        break; // 状态已更新，无需重试
                    }
                    // 检查是否还在生成中（锁仍被持有）
                    Boolean lockExists = redisTemplate.hasKey(genLockKey);
                    if (Boolean.FALSE.equals(lockExists)) {
                        // 锁已释放但状态未变，再尝试一次生成
                        checkAndGenerateReport(projectId);
                        break;
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

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

        // 确保所有严重程度级别都出现，即使数量为0
        Map<String, Long> severityMap = new LinkedHashMap<>();
        severityMap.put("CRITICAL", 0L);
        severityMap.put("MAJOR", 0L);
        severityMap.put("MINOR", 0L);
        severityMap.put("INFO", 0L);
        severityMap.putAll(severityData.stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("severity"),
                        m -> ((Number) m.get("cnt")).longValue())));
        stats.setSeverityStats(severityMap);

        // 确保所有分类都出现，即使数量为0
        Map<String, Long> categoryMap = new LinkedHashMap<>();
        categoryMap.put("SECURITY", 0L);
        categoryMap.put("BUG", 0L);
        categoryMap.put("CODE_STYLE", 0L);
        categoryMap.put("PERFORMANCE", 0L);
        categoryMap.put("BEST_PRACTICE", 0L);
        categoryMap.putAll(categoryData.stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("category"),
                        m -> ((Number) m.get("cnt")).longValue())));
        stats.setCategoryStats(categoryMap);
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

    /**
     * 手动触发项目代码风格分析并返回画像文本
     */
    public String analyzeStyle(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        return codeStyleService.analyzeAndSaveStyle(projectId);
    }

    /**
     * 获取项目当前的代码风格画像（可能为 null）
     */
    public String getStyleProfile(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        return project.getStyleProfile();
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
        // 删除旧审查任务（避免进度计算错误）
        List<ReviewTask> oldTasks = reviewTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReviewTask>()
                        .eq(ReviewTask::getProjectId, projectId));
        for (ReviewTask task : oldTasks) {
            reviewTaskMapper.deleteById(task.getId());
        }
        log.info("已清除项目[{}]的旧审查数据", projectId);
    }

    /**
     * 重置项目的审查状态（删除文件/重新上传时调用）
     */
    public void resetReviewState(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project != null) {
            project.setReviewStatus("PENDING");
            projectMapper.updateById(project);
        }
        // 清除旧审查数据
        clearOldReviewResults(projectId);
        // 释放Redis锁
        redisTemplate.delete(REVIEW_LOCK_KEY + projectId);
        redisTemplate.delete(REVIEW_PROGRESS_KEY + projectId);
        log.info("项目[{}]审查状态已重置", projectId);
    }
}
