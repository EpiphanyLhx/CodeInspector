package com.codeinspector.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建团队审查任务请求
 */
@Data
public class CreateTeamReviewTaskDTO {
    @NotNull(message = "团队ID不能为空")
    private Long teamId;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "任务标题不能为空")
    private String title;

    private String description;

    @NotBlank(message = "审查分支不能为空")
    private String reviewBranch;

    private LocalDateTime deadline;

    @NotEmpty(message = "请至少指派一名成员")
    private List<Long> assigneeIds;
}
