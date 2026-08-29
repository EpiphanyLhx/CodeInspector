package com.codeinspector.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("project")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private String name;
    private String description;
    private String sourceType;  // UPLOAD / GIT
    private String gitUrl;
    private String gitBranch;
    private String gitUsername;       // Git用户名(私有仓库认证)
    private String gitTokenEncrypted; // Git访问令牌(AES加密)
    private String repoPath;
    private String language;
    private Integer totalFiles;
    private Long totalLines;
    private String reviewStatus;  // PENDING / IN_PROGRESS / COMPLETED / FAILED
    private String styleProfile;  // 代码风格画像(自动分析生成)
    private Integer styleEnabled; // 是否启用按用户代码风格审查: 1是 0否
    private LocalDateTime styleAnalyzedAt; // 风格画像最后分析时间
    private Long creatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
