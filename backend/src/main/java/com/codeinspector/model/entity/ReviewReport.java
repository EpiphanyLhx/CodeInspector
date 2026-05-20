package com.codeinspector.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("review_report")
public class ReviewReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Integer totalIssues;
    private Integer criticalCount;
    private Integer majorCount;
    private Integer minorCount;
    private Integer infoCount;
    private Integer securityCount;
    private Integer bugCount;
    private Integer styleCount;
    private Integer performanceCount;
    private Integer bestPracticeCount;
    private BigDecimal bugRate;
    private Integer reviewedFiles;
    private Long reviewedLines;
    private Integer score;
    private String summary;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
