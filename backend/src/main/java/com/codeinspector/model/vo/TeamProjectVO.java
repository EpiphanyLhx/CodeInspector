package com.codeinspector.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TeamProjectVO {
    private Long id;
    private String name;
    private String description;
    private String sourceType;  // UPLOAD / GIT
    private String gitBranch;
    private String language;
    private String reviewStatus;
    private Long creatorId;
    private String creatorName;
    private LocalDateTime createTime;
}
