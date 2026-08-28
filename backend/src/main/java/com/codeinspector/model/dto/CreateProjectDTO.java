package com.codeinspector.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProjectDTO {
    private Long teamId;  // 可选，不填则为个人项目
    @NotBlank(message = "项目名称不能为空")
    private String name;
    private String description;
    @NotBlank(message = "来源类型不能为空")
    private String sourceType;  // UPLOAD / GIT
    private String gitUrl;
    private String gitBranch;
    private String language;
}
