package com.codeinspector.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 团队审查任务
 * 状态流转: PENDING(待提交) -> REVIEWING(审查中) -> COMPLETED(已完成) / FAILED(失败)
 * 已完成/失败的任务允许被指派成员再次提交，重新进入 REVIEWING
 */
@Data
@TableName("team_review_task")
public class TeamReviewTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long projectId;
    private String title;
    private String description;
    private String reviewBranch;
    private LocalDateTime deadline;
    private Long creatorId;
    private String status;  // PENDING / REVIEWING / COMPLETED / FAILED
    /** REVIEWING 时的子阶段: PULLING(拉取代码) / SCANNING(扫描) / AI_REVIEWING(AI审查) */
    private String stage;
    private String lastCommitHash;
    private Long lastSubmitterId;
    private LocalDateTime lastSubmitTime;
    private String errorMsg;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
