package com.codeinspector.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review_issue")
public class ReviewIssue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long projectId;
    private String filePath;
    private Integer lineStart;
    private Integer lineEnd;
    private String severity;   // CRITICAL / MAJOR / MINOR / INFO
    private String category;   // SECURITY / BUG / CODE_STYLE / PERFORMANCE / BEST_PRACTICE
    private String title;
    private String description;
    private String suggestion;
    private String codeSnippet;
    private String fixedCode;
    private String status;     // OPEN / RESOLVED / IGNORED
    private Long resolvedBy;
    private LocalDateTime resolvedTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
