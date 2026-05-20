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
    private String repoPath;
    private String language;
    private Integer totalFiles;
    private Long totalLines;
    private String reviewStatus;  // PENDING / IN_PROGRESS / COMPLETED / FAILED
    private Long creatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
