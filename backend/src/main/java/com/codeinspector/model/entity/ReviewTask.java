package com.codeinspector.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review_task")
public class ReviewTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long chunkId;
    private Long userId;
    private String status;  // PENDING / QUEUED / PROCESSING / COMPLETED / FAILED
    private String errorMsg;
    private String aiProvider;
    private String aiModel;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
