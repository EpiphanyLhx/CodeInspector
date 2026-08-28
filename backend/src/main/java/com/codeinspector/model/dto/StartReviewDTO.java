package com.codeinspector.model.dto;

import lombok.Data;

/**
 * 启动审查请求参数
 */
@Data
public class StartReviewDTO {
    /**
     * 是否按用户代码风格给出审查建议
     */
    private Boolean styleEnabled = false;
}
