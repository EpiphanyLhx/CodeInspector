package com.codeinspector.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户API密钥配置
 */
@Data
@TableName("user_api_key")
public class UserApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** AI提供商: tongyi/wenxin/openai/custom */
    private String provider;

    /** API Key (AES加密存储) */
    private String apiKeyEncrypted;

    /** Secret Key (AES加密存储, 仅部分平台需要) */
    private String secretKeyEncrypted;

    /** 自定义API端点URL */
    private String baseUrl;

    /** 使用的模型名称 */
    private String modelName;

    /** 是否当前激活: 1是 0否 */
    private Integer isActive;

    /** 是否已验证: 1已验证 0未验证 */
    private Integer isValid;

    /** 最后验证时间 */
    private LocalDateTime lastValidatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
