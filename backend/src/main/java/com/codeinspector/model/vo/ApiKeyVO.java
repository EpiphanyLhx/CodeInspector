package com.codeinspector.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 返回给前端的API Key信息（已脱敏）
 */
@Data
public class ApiKeyVO {

    private Long id;
    private String provider;
    private String providerLabel;

    /** 脱敏后的API Key，仅显示前后几位 */
    private String apiKeyMasked;

    /** 是否有Secret Key */
    private Boolean hasSecretKey;

    private String baseUrl;
    private String modelName;

    private Boolean isActive;
    private Boolean isValid;

    private LocalDateTime lastValidatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 可用的模型列表（根据provider返回） */
    private java.util.List<String> availableModels;

    /**
     * 提供商中文标签
     */
    public static String getProviderLabel(String provider) {
        return switch (provider) {
            case "tongyi" -> "通义千问 (阿里云)";
            case "wenxin" -> "文心一言 (百度)";
            case "openai" -> "OpenAI (ChatGPT)";
            case "custom" -> "自定义API";
            default -> provider;
        };
    }

    /**
     * 获取提供商可选的模型列表
     */
    public static java.util.List<String> getModelsForProvider(String provider) {
        return switch (provider) {
            case "tongyi" -> java.util.List.of(
                    "qwen-max", "qwen-plus", "qwen-turbo",
                    "qwen-max-longcontext", "qwen2.5-72b-instruct",
                    "qwen2.5-32b-instruct", "qwen2.5-14b-instruct",
                    "qwen2.5-7b-instruct", "qwen-coder-plus"
            );
            case "wenxin" -> java.util.List.of(
                    "ernie-4.0-turbo-128k", "ernie-4.0-8k", "ernie-3.5-128k",
                    "ernie-3.5-8k", "ernie-speed-128k", "ernie-speed-8k",
                    "ernie-lite-8k", "ernie-tiny-8k"
            );
            case "openai" -> java.util.List.of(
                    "gpt-4o", "gpt-4o-mini", "gpt-4-turbo",
                    "gpt-4", "gpt-3.5-turbo", "o1-preview", "o1-mini"
            );
            case "custom" -> java.util.List.of(
                    "deepseek-chat", "deepseek-reasoner",
                    "glm-4-plus", "glm-4-flash",
                    "moonshot-v1-8k", "moonshot-v1-32k"
            );
            default -> java.util.List.of();
        };
    }

    /**
     * 获取提供商的默认API端点
     */
    public static String getDefaultBaseUrl(String provider) {
        return switch (provider) {
            case "tongyi" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "wenxin" -> "https://aip.baidubce.com";
            case "openai" -> "https://api.openai.com/v1";
            case "custom" -> "";
            default -> "";
        };
    }
}
