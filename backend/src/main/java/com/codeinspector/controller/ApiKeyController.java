package com.codeinspector.controller;

import com.codeinspector.common.Result;
import com.codeinspector.model.dto.SaveApiKeyDTO;
import com.codeinspector.model.vo.ApiKeyVO;
import com.codeinspector.security.JwtTokenProvider;
import com.codeinspector.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API密钥管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final JwtTokenProvider jwtTokenProvider;

    public ApiKeyController(ApiKeyService apiKeyService, JwtTokenProvider jwtTokenProvider) {
        this.apiKeyService = apiKeyService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 获取当前用户的API Key列表
     */
    @GetMapping
    public Result<List<ApiKeyVO>> list(@RequestHeader("Authorization") String authHeader) {
        Long userId = getUserId(authHeader);
        List<ApiKeyVO> keys = apiKeyService.getUserApiKeys(userId);
        return Result.success(keys);
    }

    /**
     * 保存/更新 API Key
     */
    @PostMapping
    public Result<ApiKeyVO> save(@RequestHeader("Authorization") String authHeader,
                                  @Valid @RequestBody SaveApiKeyDTO dto) {
        Long userId = getUserId(authHeader);
        try {
            ApiKeyVO result = apiKeyService.saveApiKey(userId, dto);
            return Result.success(result);
        } catch (Exception e) {
            log.error("保存API Key失败: ", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 设为激活
     */
    @PutMapping("/{id}/activate")
    public Result<Void> activate(@RequestHeader("Authorization") String authHeader,
                                  @PathVariable Long id) {
        Long userId = getUserId(authHeader);
        apiKeyService.setActive(userId, id);
        return Result.success(null);
    }

    /**
     * 验证 API Key
     */
    @PostMapping("/{id}/validate")
    public Result<Boolean> validate(@RequestHeader("Authorization") String authHeader,
                                     @PathVariable Long id) {
        Long userId = getUserId(authHeader);
        boolean valid = apiKeyService.validateApiKey(userId, id);
        if (valid) {
            return Result.success(true);
        }
        return Result.error("API Key验证失败，请检查Key是否正确或是否已过期。请确保：\n"
                + "1. API Key已正确复制（无多余空格）\n"
                + "2. API Key账户余额充足\n"
                + "3. 如使用文心一言，需同时提供Secret Key\n"
                + "4. 自定义API需提供正确的Base URL");
    }

    /**
     * 删除 API Key
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestHeader("Authorization") String authHeader,
                                @PathVariable Long id) {
        Long userId = getUserId(authHeader);
        apiKeyService.deleteApiKey(userId, id);
        return Result.success(null);
    }

    /**
     * 获取指定provider的可用模型列表
     */
    @GetMapping("/models/{provider}")
    public Result<List<String>> getModels(@PathVariable String provider) {
        List<String> models = ApiKeyVO.getModelsForProvider(provider.toLowerCase());
        return Result.success(models);
    }

    private Long getUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
