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
    private String gitUsername;        // Git用户名(私有仓库认证), 明文返回非敏感
    private Boolean gitTokenConfigured; // 是否已配置访问令牌(不返回明文)
    private String language;
    private Integer totalFiles;
    private Long totalLines;
    private String reviewStatus;
    private Integer styleEnabled;   // 是否启用按用户代码风格审查
    private Boolean styleAnalyzed;  // 风格画像是否已生成
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
}
