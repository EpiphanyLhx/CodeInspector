package com.codeinspector.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("code_chunk")
public class CodeChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fileId;
    private Long projectId;
    private Integer chunkIndex;
    private String chunkType;  // CLASS / METHOD / BLOCK
    private String elementName;
    private String chunkContent;
    private Integer startLine;
    private Integer endLine;
    private LocalDateTime createTime;
}
