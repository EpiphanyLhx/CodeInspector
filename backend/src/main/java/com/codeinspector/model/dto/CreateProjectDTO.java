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
    private String gitUsername; // 私有仓库用户名(可选)
    private String gitToken;    // 私有仓库访问令牌(可选, 后端AES加密存储)
    private String language;
}
