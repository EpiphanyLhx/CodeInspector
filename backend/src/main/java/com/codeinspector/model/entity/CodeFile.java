package com.codeinspector.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("code_file")
public class CodeFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String filePath;
    private String fileName;
    private String fileContent;
    private String astData;
    private Integer lineCount;
    private Integer chunkCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
