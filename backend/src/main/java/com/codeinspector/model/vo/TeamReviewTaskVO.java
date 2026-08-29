package com.codeinspector.model.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 团队审查任务 VO
 */
@Data
public class TeamReviewTaskVO {
    private Long id;
    private Long teamId;
    private String teamName;
    private Long projectId;
    private String projectName;
    private String title;
    private String description;
    private String reviewBranch;
    private LocalDateTime deadline;
    private Long creatorId;
    private String creatorName;
    /** PENDING / REVIEWING / COMPLETED / FAILED */
    private String status;
    /** REVIEWING 时的子阶段: PULLING / SCANNING / AI_REVIEWING */
    private String stage;
    private String lastCommitHash;
    private Long lastSubmitterId;
    private String lastSubmitterName;
    private LocalDateTime lastSubmitTime;
    private String errorMsg;
    private LocalDateTime createTime;

    /** 被指派成员列表 */
    private List<AssigneeVO> assignees;

    /** 当前登录用户是否为团队管理员(可发布/删除) */
    private Boolean canManage;
    /** 当前登录用户是否为被指派成员(可提交) */
    private Boolean canSubmit;

    @Data
    public static class AssigneeVO {
        private Long userId;
        private String username;
        private String avatar;
    }
}
