package com.codeinspector.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProjectVO {
    private Long id;
    private Long teamId;
    private String teamName;
    private String name;
    private String description;
    private String sourceType;
    private String gitUrl;
    private String gitBranch;
    private String language;
    private Integer totalFiles;
    private Long totalLines;
    private String reviewStatus;
    private Long creatorId;
    private String creatorName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    // 统计信息
    private Integer issueCount;
    private Integer criticalCount;
    private Integer majorCount;
    private Integer minorCount;
    private Integer infoCount;
    private Integer score;
}
