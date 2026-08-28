package com.codeinspector.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TeamReviewVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long userId;
    private String status;
    private String aiModel;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}
