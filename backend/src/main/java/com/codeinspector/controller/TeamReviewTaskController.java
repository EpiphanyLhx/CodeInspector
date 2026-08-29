package com.codeinspector.controller;

import com.codeinspector.common.Result;
import com.codeinspector.model.dto.CreateTeamReviewTaskDTO;
import com.codeinspector.model.entity.User;
import com.codeinspector.model.vo.TeamReviewTaskVO;
import com.codeinspector.service.TeamReviewTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 团队审查任务
 * 发布/查看/提交/删除团队审查任务，代码拉取与 AI 审查复用现有链路
 */
@RestController
@RequestMapping("/api/team-tasks")
@RequiredArgsConstructor
public class TeamReviewTaskController {

    private final TeamReviewTaskService taskService;

    /** 发布任务（仅团队管理员 LEADER/ADMIN） */
    @PostMapping
    public Result<TeamReviewTaskVO> create(@Valid @RequestBody CreateTeamReviewTaskDTO dto,
                                           @AuthenticationPrincipal User user) {
        return Result.success("任务发布成功", taskService.createTask(dto, user.getId()));
    }

    /**
     * 任务列表
     * @param scope assigned=指派给我的, created=我发布的, all=全部可见(默认)
     */
    @GetMapping
    public Result<List<TeamReviewTaskVO>> list(@AuthenticationPrincipal User user,
                                               @RequestParam(defaultValue = "all") String scope) {
        return Result.success(taskService.listTasks(user.getId(), scope));
    }

    /** 任务详情（仅团队成员可见） */
    @GetMapping("/{taskId}")
    public Result<TeamReviewTaskVO> detail(@PathVariable Long taskId,
                                           @AuthenticationPrincipal User user) {
        return Result.success(taskService.getTaskDetail(taskId, user.getId()));
    }

    /** 提交代码并审查（仅被指派成员） */
    @PostMapping("/{taskId}/submit")
    public Result<TeamReviewTaskVO> submit(@PathVariable Long taskId,
                                           @AuthenticationPrincipal User user) {
        return Result.success("代码已拉取并开始审查", taskService.submitForReview(taskId, user.getId()));
    }

    /** 删除任务（仅团队管理员） */
    @DeleteMapping("/{taskId}")
    public Result<Void> delete(@PathVariable Long taskId,
                               @AuthenticationPrincipal User user) {
        taskService.deleteTask(taskId, user.getId());
        return Result.success();
    }
}
