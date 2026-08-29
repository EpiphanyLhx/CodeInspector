package com.codeinspector.model.dto;

import lombok.Data;

/**
 * 更新项目 Git 私有仓库凭据请求
 * gitUsername 为 null 表示不修改；gitToken 为 null 表示不修改，
 * 为空字符串 "" 表示清除已保存的令牌
 */
@Data
public class UpdateGitCredentialsDTO {
    private String gitUsername;
    private String gitToken;
}
