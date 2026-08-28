package com.codeinspector.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存API Key的DTO
 */
@Data
public class SaveApiKeyDTO {

    @NotBlank(message = "提供商不能为空")
    private String provider;

    @NotBlank(message = "API Key不能为空")
    @Size(min = 8, message = "API Key长度至少8位")
    private String apiKey;

    /** Secret Key（部分平台需要，如文心一言） */
    private String secretKey;

    /** 自定义API端点（可选，默认使用系统预置端点） */
    private String baseUrl;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    /** 是否设为激活状态 */
    private Boolean setActive = true;
}
