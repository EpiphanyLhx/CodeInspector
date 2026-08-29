package com.codeinspector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codeinspector.common.AESUtils;
import com.codeinspector.common.BusinessException;
import com.codeinspector.mapper.*;
import com.codeinspector.model.dto.CreateTeamReviewTaskDTO;
import com.codeinspector.model.entity.*;
import com.codeinspector.model.vo.TeamReviewTaskVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 团队审查任务服务
 * <p>职责：发布任务、成员提交代码并触发 AI 审查（复用 {@link ReviewService#startReview}）、
 * 审查完成后自动更新任务状态、任务列表/详情/删除。</p>
 * <p>不做在线编辑、实时协同、代码合并/MR。</p>
 */
@Slf4j
@Service
public class TeamReviewTaskService implements ReviewService.ReviewCompletionListener {

    private final TeamReviewTaskMapper taskMapper;
    private final TeamReviewTaskAssigneeMapper assigneeMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final CodeFileMapper codeFileMapper;
    private final CodeChunkMapper codeChunkMapper;
    private final CodeAnalysisService codeAnalysisService;
    private final GitService gitService;
    private final AESUtils aesUtils;
    private final ReviewService reviewService;
    private final TeamTaskSubmitExecutor submitExecutor;

    /** 自注入，用于在非事务方法中调用 @Transactional 方法时走代理 */
    @Lazy
    @Autowired
    private TeamReviewTaskService self;

    public TeamReviewTaskService(TeamReviewTaskMapper taskMapper,
                                 TeamReviewTaskAssigneeMapper assigneeMapper,
                                 TeamMapper teamMapper,
                                 TeamMemberMapper teamMemberMapper,
                                 ProjectMapper projectMapper,
                                 UserMapper userMapper,
                                 CodeFileMapper codeFileMapper,
                                 CodeChunkMapper codeChunkMapper,
                                 CodeAnalysisService codeAnalysisService,
                                 GitService gitService,
                                 AESUtils aesUtils,
                                 @Lazy ReviewService reviewService,
                                 @Lazy TeamTaskSubmitExecutor submitExecutor) {
        this.taskMapper = taskMapper;
        this.assigneeMapper = assigneeMapper;
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.codeFileMapper = codeFileMapper;
        this.codeChunkMapper = codeChunkMapper;
        this.codeAnalysisService = codeAnalysisService;
        this.gitService = gitService;
        this.aesUtils = aesUtils;
        this.reviewService = reviewService;
        this.submitExecutor = submitExecutor;
    }

    // ==================== 权限校验 ====================

    private TeamMember getMembership(Long teamId, Long userId) {
        return teamMemberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
    }

    private TeamMember requireTeamMember(Long teamId, Long userId) {
        TeamMember m = getMembership(teamId, userId);
        if (m == null) {
            throw new BusinessException("你不是该团队成员");
        }
        return m;
    }

    private TeamMember requireTeamAdmin(Long teamId, Long userId) {
        TeamMember m = requireTeamMember(teamId, userId);
        if (!"LEADER".equals(m.getRole()) && !"ADMIN".equals(m.getRole())) {
            throw new BusinessException("只有团队管理员可以执行此操作");
        }
        return m;
    }

    private boolean isTeamAdmin(Long teamId, Long userId) {
        TeamMember m = getMembership(teamId, userId);
        return m != null && ("LEADER".equals(m.getRole()) || "ADMIN".equals(m.getRole()));
    }

    private boolean isAssignee(Long taskId, Long userId) {
        Long count = assigneeMapper.selectCount(new LambdaQueryWrapper<TeamReviewTaskAssignee>()
                .eq(TeamReviewTaskAssignee::getTaskId, taskId)
                .eq(TeamReviewTaskAssignee::getUserId, userId));
        return count != null && count > 0;
    }

    // ==================== 发布任务 ====================

    @Transactional
    public TeamReviewTaskVO createTask(CreateTeamReviewTaskDTO dto, Long creatorId) {
        // 仅团队管理员可发布
        requireTeamAdmin(dto.getTeamId(), creatorId);

        // 校验项目：属于该团队且为 GIT 类型
        Project project = projectMapper.selectById(dto.getProjectId());
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (!dto.getTeamId().equals(project.getTeamId())) {
            throw new BusinessException("只能选择本团队下的项目");
        }
        if (!"GIT".equals(project.getSourceType())) {
            throw new BusinessException("只能选择 Git 类型的项目");
        }
        if (project.getGitUrl() == null || project.getGitUrl().isBlank()) {
            throw new BusinessException("项目未配置 Git 仓库地址");
        }

        // 校验指派成员：去重、非空、均为团队成员
        if (dto.getAssigneeIds() == null || dto.getAssigneeIds().isEmpty()) {
            throw new BusinessException("请至少指派一名成员");
        }
        List<Long> assigneeIds = dto.getAssigneeIds().stream()
                .filter(Objects::nonNull).distinct().toList();
        if (assigneeIds.isEmpty()) {
            throw new BusinessException("请至少指派一名成员");
        }
        for (Long uid : assigneeIds) {
            if (getMembership(dto.getTeamId(), uid) == null) {
                throw new BusinessException("用户ID " + uid + " 不是该团队成员");
            }
        }

        TeamReviewTask task = new TeamReviewTask();
        task.setTeamId(dto.getTeamId());
        task.setProjectId(dto.getProjectId());
        task.setTitle(dto.getTitle().trim());
        task.setDescription(dto.getDescription());
        task.setReviewBranch(dto.getReviewBranch().trim());
        task.setDeadline(dto.getDeadline());
        task.setCreatorId(creatorId);
        task.setStatus("PENDING");
        taskMapper.insert(task);

        for (Long uid : assigneeIds) {
            TeamReviewTaskAssignee a = new TeamReviewTaskAssignee();
            a.setTaskId(task.getId());
            a.setUserId(uid);
            assigneeMapper.insert(a);
        }
        log.info("团队审查任务[{}]已发布: team={}, project={}, branch={}, assignees={}",
                task.getId(), dto.getTeamId(), dto.getProjectId(), task.getReviewBranch(), assigneeIds);

        return getTaskDetail(task.getId(), creatorId);
    }

    // ==================== 任务列表 ====================

    /**
     * 查询当前用户可见的任务。
     * @param scope assigned=指派给我的, created=我发布的, all=全部可见(默认)
     */
    public List<TeamReviewTaskVO> listTasks(Long userId, String scope) {
        String s = scope == null ? "all" : scope.trim().toLowerCase();
        List<TeamReviewTask> tasks;

        switch (s) {
            case "created" -> {
                tasks = taskMapper.selectList(new LambdaQueryWrapper<TeamReviewTask>()
                        .eq(TeamReviewTask::getCreatorId, userId)
                        .orderByDesc(TeamReviewTask::getCreateTime));
            }
            case "assigned" -> {
                List<Long> taskIds = assigneeMapper.selectList(
                                new LambdaQueryWrapper<TeamReviewTaskAssignee>()
                                        .eq(TeamReviewTaskAssignee::getUserId, userId))
                        .stream().map(TeamReviewTaskAssignee::getTaskId).toList();
                if (taskIds.isEmpty()) {
                    tasks = List.of();
                } else {
                    tasks = taskMapper.selectList(new LambdaQueryWrapper<TeamReviewTask>()
                            .in(TeamReviewTask::getId, taskIds)
                            .orderByDesc(TeamReviewTask::getCreateTime));
                }
            }
            default -> {
                // 我所在团队的任务 + 我发布的 + 指派给我的
                List<Long> teamIds = teamMemberMapper.selectList(
                                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId))
                        .stream().map(TeamMember::getTeamId).toList();
                List<Long> assignedTaskIds = assigneeMapper.selectList(
                                new LambdaQueryWrapper<TeamReviewTaskAssignee>()
                                        .eq(TeamReviewTaskAssignee::getUserId, userId))
                        .stream().map(TeamReviewTaskAssignee::getTaskId).toList();

                LambdaQueryWrapper<TeamReviewTask> wrapper = new LambdaQueryWrapper<>();
                wrapper.and(w -> {
                    if (!teamIds.isEmpty()) {
                        w.in(TeamReviewTask::getTeamId, teamIds);
                    }
                    if (!assignedTaskIds.isEmpty()) {
                        if (!teamIds.isEmpty()) w.or();
                        w.in(TeamReviewTask::getId, assignedTaskIds);
                    }
                    if (teamIds.isEmpty() && assignedTaskIds.isEmpty()) {
                        w.eq(TeamReviewTask::getCreatorId, userId);
                    } else {
                        w.or().eq(TeamReviewTask::getCreatorId, userId);
                    }
                });
                wrapper.orderByDesc(TeamReviewTask::getCreateTime);
                tasks = taskMapper.selectList(wrapper);
            }
        }

        // 仅团队成员可见团队任务（二次过滤，防止已退出团队的任务泄露）
        tasks = tasks.stream()
                .filter(t -> getMembership(t.getTeamId(), userId) != null)
                .toList();

        return toVOList(tasks, userId);
    }

    // ==================== 任务详情 ====================

    public TeamReviewTaskVO getTaskDetail(Long taskId, Long userId) {
        TeamReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        requireTeamMember(task.getTeamId(), userId);
        return toVO(task, userId, null, null, null, null, null);
    }

    // ==================== 提交代码并审查 ====================

    /**
     * 成员提交代码并审查（同步受理，慢操作异步执行）。
     * <p>本方法只做权限校验和状态受理并立即返回；Git 拉取、代码扫描、触发 AI 审查等
     * 慢操作交由 {@link TeamTaskSubmitExecutor} 在后台线程执行，避免 clone/pull 耗时长
     * 导致 HTTP 请求超时。前端通过轮询任务详情获取 PULLING/SCANNING/AI_REVIEWING 进度。</p>
     */
    public TeamReviewTaskVO submitForReview(Long taskId, Long userId) {
        TeamReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        requireTeamMember(task.getTeamId(), userId);
        if (!isAssignee(taskId, userId)) {
            throw new BusinessException("只有被指派成员可以提交代码");
        }
        if ("REVIEWING".equals(task.getStatus())) {
            throw new BusinessException(busyHint(task.getStage()));
        }

        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (!"GIT".equals(project.getSourceType())) {
            throw new BusinessException("项目不是 Git 类型，无法提交");
        }
        if (project.getGitUrl() == null || project.getGitUrl().isBlank()) {
            throw new BusinessException("项目未配置 Git 仓库地址");
        }

        // 受理：立即进入 REVIEWING/PULLING（独立事务），随后后台执行
        (self != null ? self : this).markSubmissionAccepted(taskId, userId);

        // 后台执行拉取/扫描/审查
        submitExecutor.submitAsync(taskId, userId);

        log.info("团队审查任务[{}]成员[{}]提交已受理，后台开始拉取代码", taskId, userId);
        return getTaskDetail(taskId, userId);
    }

    /**
     * 受理提交：任务置为 REVIEWING/PULLING，记录提交人并清空上次结果（独立事务）
     */
    @Transactional
    public void markSubmissionAccepted(Long taskId, Long userId) {
        TeamReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if ("REVIEWING".equals(task.getStatus())) {
            throw new BusinessException(busyHint(task.getStage()));
        }
        task.setStatus("REVIEWING");
        task.setStage("PULLING");
        task.setLastSubmitterId(userId);
        task.setLastSubmitTime(LocalDateTime.now());
        task.setErrorMsg(null);
        taskMapper.updateById(task);
    }

    /**
     * 后台实际执行：拉取代码 -> 扫描 -> 触发 AI 审查。
     * 任何环节失败都将任务置为 FAILED 并记录原因，供前端轮询展示。
     */
    public void executeSubmission(Long taskId, Long userId) {
        TeamReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("后台执行时任务[{}]不存在", taskId);
            return;
        }
        Project project = projectMapper.selectById(task.getProjectId());
        if (project == null) {
            markTaskFailed(taskId, "项目不存在");
            return;
        }

        // 解密私有仓库凭据
        String username = project.getGitUsername();
        String token = null;
        if (project.getGitTokenEncrypted() != null && !project.getGitTokenEncrypted().isBlank()) {
            try {
                token = aesUtils.decrypt(project.getGitTokenEncrypted());
            } catch (Exception e) {
                markTaskFailed(taskId, "Git 访问令牌解密失败: " + rootMessage(e));
                return;
            }
        }

        // 1. 同步仓库（慢操作）
        updateStage(taskId, "PULLING");
        GitService.GitSyncResult syncResult;
        try {
            syncResult = gitService.syncProjectRepository(
                    project.getId(), project.getGitUrl(), task.getReviewBranch(), username, token);
        } catch (Exception e) {
            log.error("任务[{}]代码拉取失败: ", taskId, e);
            markTaskFailed(taskId, "代码拉取失败: " + rootMessage(e)
                    + "（请检查仓库地址、分支、网络或 Git 代理/凭据配置）");
            return;
        }
        if (syncResult == null || syncResult.commitHash() == null) {
            markTaskFailed(taskId, "代码拉取失败：无法获取最新提交记录");
            return;
        }

        // 2. 重新扫描代码并写入 commit（独立事务）
        updateStage(taskId, "SCANNING");
        try {
            boolean hasCode = (self != null ? self : this).rescanAfterPull(
                    project.getId(), task.getReviewBranch(), syncResult);
            if (!hasCode) {
                markTaskFailed(taskId, "未在分支 " + task.getReviewBranch()
                        + " 中找到可审查的代码文件，请确认分支正确且已提交代码");
                return;
            }
        } catch (Exception e) {
            log.error("任务[{}]代码扫描失败: ", taskId, e);
            markTaskFailed(taskId, "代码扫描失败: " + rootMessage(e));
            return;
        }

        // 3. 触发 AI 审查（复用现有链路）
        updateStage(taskId, "AI_REVIEWING");
        boolean styleEnabled = project.getStyleEnabled() != null && project.getStyleEnabled() == 1;
        try {
            // 重新读取项目（rescan 更新了元数据）
            Project latest = projectMapper.selectById(project.getId());
            if (latest != null) {
                styleEnabled = latest.getStyleEnabled() != null && latest.getStyleEnabled() == 1;
            }
            reviewService.startReview(project.getId(), userId, styleEnabled);
        } catch (Exception e) {
            log.error("任务[{}]触发审查失败: ", taskId, e);
            String msg = rootMessage(e);
            // 并发提交时可能已有一次审查在运行（Redis 锁），此时保持 REVIEWING，等待其完成回调
            TeamReviewTask latest = taskMapper.selectById(taskId);
            if (latest != null && "REVIEWING".equals(latest.getStatus())
                    && msg != null && msg.contains("正在审查中")) {
                log.info("任务[{}]已有审查在运行，保持 REVIEWING 状态", taskId);
            } else {
                markTaskFailed(taskId, "触发审查失败: " + msg);
            }
        }
    }

    /**
     * 拉取完成后重新扫描代码（独立事务）：删除旧代码文件/切片 -> 重新分析 -> 更新项目元数据，
     * 并写入本次 commit hash、阶段置为 AI_REVIEWING。
     * @return 是否扫描到可审查代码
     */
    @Transactional
    public boolean rescanAfterPull(Long projectId, String branch, GitService.GitSyncResult syncResult) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }

        // 删除旧的代码文件与切片
        List<CodeFile> oldFiles = codeFileMapper.findByProjectId(projectId);
        for (CodeFile f : oldFiles) {
            codeChunkMapper.delete(new LambdaQueryWrapper<CodeChunk>()
                    .eq(CodeChunk::getFileId, f.getId()));
            codeFileMapper.deleteById(f.getId());
        }
        codeChunkMapper.delete(new LambdaQueryWrapper<CodeChunk>()
                .eq(CodeChunk::getProjectId, projectId));

        // 重新扫描
        List<CodeFile> codeFiles = codeAnalysisService.analyzeProjectCode(projectId, syncResult.repoPath());
        if (codeFiles == null || codeFiles.isEmpty()) {
            return false;
        }
        long totalLines = codeFiles.stream()
                .mapToLong(f -> f.getLineCount() != null ? f.getLineCount() : 0).sum();

        // 更新项目元数据
        project.setRepoPath(syncResult.repoPath());
        project.setGitBranch(branch);
        project.setTotalFiles(codeFiles.size());
        project.setTotalLines(totalLines);
        project.setReviewStatus("PENDING");
        projectMapper.updateById(project);
        // 代码变更后旧风格画像失效，显式清空（保留 styleEnabled 偏好）
        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getId, projectId)
                .set(Project::getStyleProfile, null)
                .set(Project::getStyleAnalyzedAt, null));

        // 写入本次提交的 commit hash，阶段进入 AI 审查
        TeamReviewTask task = taskMapper.selectOne(new LambdaQueryWrapper<TeamReviewTask>()
                .eq(TeamReviewTask::getProjectId, projectId)
                .eq(TeamReviewTask::getStatus, "REVIEWING")
                .last("LIMIT 1"));
        if (task != null) {
            task.setLastCommitHash(syncResult.commitHash());
            task.setStage("AI_REVIEWING");
            task.setErrorMsg(null);
            taskMapper.updateById(task);
        }
        return true;
    }

    private void updateStage(Long taskId, String stage) {
        TeamReviewTask t = taskMapper.selectById(taskId);
        if (t != null && "REVIEWING".equals(t.getStatus())) {
            t.setStage(stage);
            taskMapper.updateById(t);
        }
    }

    private String busyHint(String stage) {
        String where = "PULLING".equals(stage) ? "正在拉取代码"
                : "SCANNING".equals(stage) ? "正在扫描代码"
                : "正在审查";
        return "任务" + where + "中，请等待本次完成后再提交";
    }

    // ==================== 删除任务 ====================

    @Transactional
    public void deleteTask(Long taskId, Long userId) {
        TeamReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        requireTeamAdmin(task.getTeamId(), userId);
        if ("REVIEWING".equals(task.getStatus())) {
            throw new BusinessException("任务正在审查中，无法删除");
        }
        assigneeMapper.delete(new LambdaQueryWrapper<TeamReviewTaskAssignee>()
                .eq(TeamReviewTaskAssignee::getTaskId, taskId));
        taskMapper.deleteById(taskId);
        log.info("团队审查任务[{}]已被用户[{}]删除", taskId, userId);
    }

    // ==================== 审查完成回调 ====================

    /**
     * 挂钩 ReviewService.afterTaskComplete：项目审查进入终态时，
     * 自动将处于 REVIEWING 的团队任务更新为 COMPLETED/FAILED。
     */
    @Override
    public void onReviewComplete(Long projectId, String finalStatus) {
        TeamReviewTask task = taskMapper.selectOne(new LambdaQueryWrapper<TeamReviewTask>()
                .eq(TeamReviewTask::getProjectId, projectId)
                .eq(TeamReviewTask::getStatus, "REVIEWING")
                .last("LIMIT 1"));
        if (task == null) {
            return;
        }
        String taskStatus = "COMPLETED".equals(finalStatus) ? "COMPLETED" : "FAILED";
        task.setStatus(taskStatus);
        task.setStage(null);
        if ("FAILED".equals(taskStatus)) {
            task.setErrorMsg("审查未能全部完成，请点击查看项目审查报告获取详情");
        }
        taskMapper.updateById(task);
        log.info("团队审查任务[{}]状态自动更新为{}（项目[{}]审查{}）",
                task.getId(), taskStatus, projectId, finalStatus);
    }

    // ==================== 内部工具 ====================

    private void markTaskFailed(Long taskId, String errorMsg) {
        TeamReviewTask t = taskMapper.selectById(taskId);
        if (t != null) {
            t.setStatus("FAILED");
            t.setStage(null);
            t.setErrorMsg(errorMsg != null && errorMsg.length() > 900
                    ? errorMsg.substring(0, 900) : errorMsg);
            taskMapper.updateById(t);
            log.warn("团队审查任务[{}]失败: {}", taskId, errorMsg);
        }
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    // ==================== VO 组装 ====================

    private List<TeamReviewTaskVO> toVOList(List<TeamReviewTask> tasks, Long currentUserId) {
        if (tasks.isEmpty()) return List.of();

        Set<Long> teamIds = tasks.stream().map(TeamReviewTask::getTeamId).collect(Collectors.toSet());
        Set<Long> projectIds = tasks.stream().map(TeamReviewTask::getProjectId).collect(Collectors.toSet());
        Set<Long> userIds = new HashSet<>();
        tasks.forEach(t -> {
            if (t.getCreatorId() != null) userIds.add(t.getCreatorId());
            if (t.getLastSubmitterId() != null) userIds.add(t.getLastSubmitterId());
        });

        Map<Long, String> teamNameMap = teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, Team::getName));
        Map<Long, String> projectNameMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // 批量查询指派成员
        List<Long> taskIds = tasks.stream().map(TeamReviewTask::getId).toList();
        List<TeamReviewTaskAssignee> allAssignees = assigneeMapper.selectList(
                new LambdaQueryWrapper<TeamReviewTaskAssignee>()
                        .in(TeamReviewTaskAssignee::getTaskId, taskIds));
        Set<Long> assigneeUserIds = allAssignees.stream()
                .map(TeamReviewTaskAssignee::getUserId).collect(Collectors.toSet());
        Map<Long, User> assigneeUserMap = assigneeUserIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(assigneeUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<TeamReviewTaskAssignee>> assigneesByTask = allAssignees.stream()
                .collect(Collectors.groupingBy(TeamReviewTaskAssignee::getTaskId));

        // 当前用户在各团队的角色
        Map<Long, String> myRoleMap = teamMemberMapper.selectList(
                        new LambdaQueryWrapper<TeamMember>()
                                .eq(TeamMember::getUserId, currentUserId)
                                .in(TeamMember::getTeamId, teamIds))
                .stream().collect(Collectors.toMap(TeamMember::getTeamId, TeamMember::getRole));

        return tasks.stream().map(t -> {
            String myRole = myRoleMap.get(t.getTeamId());
            boolean admin = "LEADER".equals(myRole) || "ADMIN".equals(myRole);
            List<TeamReviewTaskAssignee> taskAssignees =
                    assigneesByTask.getOrDefault(t.getId(), List.of());
            boolean canSubmit = taskAssignees.stream()
                    .anyMatch(a -> currentUserId.equals(a.getUserId()));
            return assembleVO(t, currentUserId, teamNameMap, projectNameMap,
                    userMap, assigneeUserMap, taskAssignees, admin, canSubmit);
        }).toList();
    }

    private TeamReviewTaskVO toVO(TeamReviewTask task, Long currentUserId,
                                  Map<Long, String> teamNameMap,
                                  Map<Long, String> projectNameMap,
                                  Map<Long, User> userMap,
                                  Map<Long, User> assigneeUserMap,
                                  List<TeamReviewTaskAssignee> preloadedAssignees) {
        String myRole = Optional.ofNullable(getMembership(task.getTeamId(), currentUserId))
                .map(TeamMember::getRole).orElse(null);
        boolean admin = "LEADER".equals(myRole) || "ADMIN".equals(myRole);

        // 按需批量/单条查询缺失数据
        Map<Long, String> tnMap = teamNameMap;
        Map<Long, String> pnMap = projectNameMap;
        Map<Long, User> uMap = userMap;
        Map<Long, User> auMap = assigneeUserMap;
        List<TeamReviewTaskAssignee> taskAssignees = preloadedAssignees;

        if (tnMap == null) {
            Team team = teamMapper.selectById(task.getTeamId());
            tnMap = Map.of(task.getTeamId(), team != null ? team.getName() : null);
        }
        if (pnMap == null) {
            Project project = projectMapper.selectById(task.getProjectId());
            pnMap = Map.of(task.getProjectId(), project != null ? project.getName() : null);
        }
        if (uMap == null) {
            Set<Long> ids = new HashSet<>();
            if (task.getCreatorId() != null) ids.add(task.getCreatorId());
            if (task.getLastSubmitterId() != null) ids.add(task.getLastSubmitterId());
            uMap = ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
        }
        if (taskAssignees == null) {
            taskAssignees = assigneeMapper.selectList(new LambdaQueryWrapper<TeamReviewTaskAssignee>()
                    .eq(TeamReviewTaskAssignee::getTaskId, task.getId()));
        }
        if (auMap == null) {
            Set<Long> ids = taskAssignees.stream()
                    .map(TeamReviewTaskAssignee::getUserId).collect(Collectors.toSet());
            auMap = ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
        }

        boolean canSubmit = taskAssignees.stream()
                .anyMatch(a -> currentUserId.equals(a.getUserId()));
        return assembleVO(task, currentUserId, tnMap, pnMap, uMap, auMap,
                taskAssignees, admin, canSubmit);
    }

    private TeamReviewTaskVO assembleVO(TeamReviewTask task, Long currentUserId,
                                        Map<Long, String> teamNameMap,
                                        Map<Long, String> projectNameMap,
                                        Map<Long, User> userMap,
                                        Map<Long, User> assigneeUserMap,
                                        List<TeamReviewTaskAssignee> taskAssignees,
                                        boolean canManage, boolean canSubmit) {
        TeamReviewTaskVO vo = new TeamReviewTaskVO();
        vo.setId(task.getId());
        vo.setTeamId(task.getTeamId());
        vo.setTeamName(teamNameMap.get(task.getTeamId()));
        vo.setProjectId(task.getProjectId());
        vo.setProjectName(projectNameMap.get(task.getProjectId()));
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setReviewBranch(task.getReviewBranch());
        vo.setDeadline(task.getDeadline());
        vo.setCreatorId(task.getCreatorId());
        User creator = userMap.get(task.getCreatorId());
        vo.setCreatorName(creator != null ? creator.getUsername() : null);
        vo.setStatus(task.getStatus());
        vo.setStage(task.getStage());
        vo.setLastCommitHash(task.getLastCommitHash());
        vo.setLastSubmitterId(task.getLastSubmitterId());
        User submitter = userMap.get(task.getLastSubmitterId());
        vo.setLastSubmitterName(submitter != null ? submitter.getUsername() : null);
        vo.setLastSubmitTime(task.getLastSubmitTime());
        vo.setErrorMsg(task.getErrorMsg());
        vo.setCreateTime(task.getCreateTime());

        List<TeamReviewTaskVO.AssigneeVO> assigneeVOs = taskAssignees.stream().map(a -> {
            TeamReviewTaskVO.AssigneeVO av = new TeamReviewTaskVO.AssigneeVO();
            av.setUserId(a.getUserId());
            User u = assigneeUserMap.get(a.getUserId());
            if (u != null) {
                av.setUsername(u.getUsername());
                av.setAvatar(u.getAvatar());
            }
            return av;
        }).toList();
        vo.setAssignees(assigneeVOs);

        vo.setCanManage(canManage);
        vo.setCanSubmit(canSubmit);
        return vo;
    }
}
