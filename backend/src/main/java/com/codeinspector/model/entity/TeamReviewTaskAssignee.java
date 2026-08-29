package com.codeinspector.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 团队审查任务 - 被指派成员关联表
 */
@Data
@TableName("team_review_task_assignee")
public class TeamReviewTaskAssignee {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long userId;
    private LocalDateTime createTime;
}
