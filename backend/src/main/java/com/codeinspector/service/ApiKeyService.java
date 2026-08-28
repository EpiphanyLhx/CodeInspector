package com.codeinspector.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.codeinspector.common.AESUtils;
import com.codeinspector.common.BusinessException;
import com.codeinspector.mapper.UserApiKeyMapper;
import com.codeinspector.model.dto.SaveApiKeyDTO;
import com.codeinspector.model.entity.UserApiKey;
import com.codeinspector.model.vo.ApiKeyVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * API Key 管理服务
 */
@Slf4j
@Service
public class ApiKeyService {

    private final UserApiKeyMapper apiKeyMapper;
    private final AESUtils aesUtils;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public ApiKeyService(UserApiKeyMapper apiKeyMapper, AESUtils aesUtils) {
        this.apiKeyMapper = apiKeyMapper;
        this.aesUtils = aesUtils;
    }

    /**
     * 获取用户的所有 API Key 配置（脱敏后返回）
     */
    public List<ApiKeyVO> getUserApiKeys(Long userId) {
        List<UserApiKey> keys = apiKeyMapper.selectList(
                new LambdaQueryWrapper<UserApiKey>()
                        .eq(UserApiKey::getUserId, userId)
                        .orderByDesc(UserApiKey::getIsActive)
                        .orderByDesc(UserApiKey::getUpdateTime));

        return keys.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 保存或更新 API Key
     */
    @Transactional
    public ApiKeyVO saveApiKey(Long userId, SaveApiKeyDTO dto) {
        String provider = dto.getProvider().toLowerCase();

        // 查找该用户的同provider配置（更新）或创建新的
        UserApiKey apiKey = apiKeyMapper.selectOne(
                new LambdaQueryWrapper<UserApiKey>()
                        .eq(UserApiKey::getUserId, userId)
                        .eq(UserApiKey::getProvider, provider));

        boolean isNew = (apiKey == null);
        if (isNew) {
            apiKey = new UserApiKey();
            apiKey.setUserId(userId);
            apiKey.setProvider(provider);
        }

        // 加密存储 API Key
        apiKey.setApiKeyEncrypted(aesUtils.encrypt(dto.getApiKey()));

        // Secret Key 加密存储
        if (dto.getSecretKey() != null && !dto.getSecretKey().isEmpty()) {
            apiKey.setSecretKeyEncrypted(aesUtils.encrypt(dto.getSecretKey()));
        } else {
            apiKey.setSecretKeyEncrypted(null);
        }

        // Base URL（未提供则使用默认值，custom 则尝试从模型名推断）
        apiKey.setBaseUrl(resolveBaseUrl(provider, dto.getBaseUrl(), dto.getModelName()));

        apiKey.setModelName(dto.getModelName());
        apiKey.setIsValid(0);
        apiKey.setLastValidatedAt(null);

        if (isNew) {
            apiKeyMapper.insert(apiKey);
        } else {
            apiKeyMapper.updateById(apiKey);
        }

        // 设为激活
        if (dto.getSetActive() != null && dto.getSetActive()) {
            setActive(userId, apiKey.getId());
        }

        // 异步验证（不阻塞保存流程）
        try {
            validateAndUpdate(apiKey.getId(), userId);
        } catch (Exception e) {
            log.warn("API Key验证失败（已保存，用户可手动验证）: {}", e.getMessage());
        }

        // 重新查询获取最新状态
        UserApiKey updated = apiKeyMapper.selectById(apiKey.getId());
        return toVO(updated);
    }

    /**
     * 设为激活
     */
    @Transactional
    public void setActive(Long userId, Long keyId) {
        UserApiKey target = apiKeyMapper.selectById(keyId);
        if (target == null || !target.getUserId().equals(userId)) {
            throw new BusinessException("API Key配置不存在");
        }

        // 取消其他key的激活状态
        List<UserApiKey> allKeys = apiKeyMapper.selectList(
                new LambdaQueryWrapper<UserApiKey>().eq(UserApiKey::getUserId, userId));
        for (UserApiKey key : allKeys) {
            if (key.getIsActive() == 1) {
                key.setIsActive(0);
                apiKeyMapper.updateById(key);
            }
        }

        // 激活目标key
        target.setIsActive(1);
        apiKeyMapper.updateById(target);
    }

    /**
     * 删除 API Key
     */
    @Transactional
    public void deleteApiKey(Long userId, Long keyId) {
        UserApiKey key = apiKeyMapper.selectById(keyId);
        if (key == null || !key.getUserId().equals(userId)) {
            throw new BusinessException("API Key配置不存在");
        }
        apiKeyMapper.deleteById(keyId);
    }

    /**
     * 验证 API Key 有效性
     */
    public boolean validateApiKey(Long userId, Long keyId) {
        return validateAndUpdate(keyId, userId);
    }

    /**
     * 执行验证并更新状态
     */
    private boolean validateAndUpdate(Long keyId, Long userId) {
        UserApiKey key = apiKeyMapper.selectById(keyId);
        if (key == null || !key.getUserId().equals(userId)) {
            return false;
        }

        String apiKeyPlain = aesUtils.decrypt(key.getApiKeyEncrypted());
        if (apiKeyPlain == null || apiKeyPlain.isEmpty()) {
            updateValidationStatus(key, false);
            return false;
        }

        boolean valid;
        try {
            valid = switch (key.getProvider()) {
                case "tongyi" -> validateTongyi(apiKeyPlain, key.getBaseUrl());
                case "wenxin" -> {
                    String secretKeyPlain = key.getSecretKeyEncrypted() != null
                            ? aesUtils.decrypt(key.getSecretKeyEncrypted()) : null;
                    yield validateWenxin(apiKeyPlain, secretKeyPlain);
                }
                case "openai" -> validateOpenAI(apiKeyPlain, key.getBaseUrl());
                case "custom" -> {
                    String resolved = resolveBaseUrl("custom", key.getBaseUrl(), key.getModelName());
                    // 如果推断出了 baseUrl 但 DB 中为空，回写
                    if ((key.getBaseUrl() == null || key.getBaseUrl().isEmpty())
                            && resolved != null && !resolved.isEmpty()) {
                        key.setBaseUrl(resolved);
                        apiKeyMapper.updateById(key);
                        log.info("已为自定义API[{}]自动填充端点: {}", key.getModelName(), resolved);
                    }
                    yield validateCustom(apiKeyPlain, resolved, key.getModelName());
                }
                default -> false;
            };
        } catch (Exception e) {
            log.warn("API Key验证异常: {}", e.getMessage());
            valid = false;
        }

        updateValidationStatus(key, valid);
        return valid;
    }

    private void updateValidationStatus(UserApiKey key, boolean valid) {
        key.setIsValid(valid ? 1 : 0);
        key.setLastValidatedAt(LocalDateTime.now());
        apiKeyMapper.updateById(key);
    }

    // ================ 各平台验证 ================

    /**
     * 验证通义千问 Key - 发送简单请求验证
     */
    private boolean validateTongyi(String apiKey, String baseUrl) throws IOException {
        String url = (baseUrl != null && !baseUrl.isEmpty())
                ? baseUrl + "/chat/completions"
                : "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

        JSONObject body = new JSONObject();
        body.put("model", "qwen-turbo");
        body.put("max_tokens", 5);
        JSONArray messages = new JSONArray();
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", "hi");
        messages.add(msg);
        body.put("messages", messages);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                return respBody.contains("choices") && !respBody.contains("\"code\":\"InvalidApiKey\"");
            }
            String errBody = response.body() != null ? response.body().string() : "";
            log.warn("通义千问Key验证失败 {}: {}", response.code(), errBody);
            return false;
        }
    }

    /**
     * 验证文心一言 Key - 获取AccessToken来验证
     */
    private boolean validateWenxin(String apiKey, String secretKey) throws IOException {
        if (secretKey == null || secretKey.isEmpty()) {
            log.warn("文心一言需要提供Secret Key");
            return false;
        }
        String url = String.format(
                "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=%s&client_secret=%s",
                apiKey, secretKey);

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                JSONObject json = JSON.parseObject(respBody);
                return json.containsKey("access_token");
            }
            return false;
        }
    }

    /**
     * 验证 OpenAI Key - 列出模型
     */
    private boolean validateOpenAI(String apiKey, String baseUrl) throws IOException {
        String url = (baseUrl != null && !baseUrl.isEmpty())
                ? baseUrl + "/models"
                : "https://api.openai.com/v1/models";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return true;
            }
            String errBody = response.body() != null ? response.body().string() : "";
            log.warn("OpenAI Key验证失败 {}: {}", response.code(), errBody);
            return false;
        }
    }

    /**
     * 验证自定义 API Key - 尝试 OpenAI 兼容接口
     */
    private boolean validateCustom(String apiKey, String baseUrl, String model) throws IOException {
        // 自动推断 baseUrl
        String resolvedUrl = (baseUrl != null && !baseUrl.isEmpty())
                ? baseUrl
                : inferBaseUrlFromModel(model);

        if (resolvedUrl == null || resolvedUrl.isEmpty()) {
            log.warn("自定义API需要提供baseUrl，模型[{}]无法自动推断端点", model);
            return false;
        }

        String url = resolvedUrl.endsWith("/") ? resolvedUrl + "chat/completions" : resolvedUrl + "/chat/completions";

        JSONObject body = new JSONObject();
        body.put("model", model != null ? model : "default");
        body.put("max_tokens", 5);
        JSONArray messages = new JSONArray();
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", "hi");
        messages.add(msg);
        body.put("messages", messages);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String respBody = response.body() != null ? response.body().string() : "";
                return respBody.contains("choices");
            }
            String errBody = response.body() != null ? response.body().string() : "";
            log.warn("自定义API Key验证失败 {}: {}", response.code(), errBody);
            return false;
        }
    }

    /**
     * 根据模型名推断 Base URL
     */
    private String inferBaseUrlFromModel(String modelName) {
        if (modelName == null) return null;
        String lower = modelName.toLowerCase();
        if (lower.contains("deepseek")) {
            return "https://api.deepseek.com/v1";
        }
        if (lower.contains("glm") || lower.contains("chatglm")) {
            return "https://open.bigmodel.cn/api/paas/v4";
        }
        if (lower.contains("moonshot") || lower.contains("kimi")) {
            return "https://api.moonshot.cn/v1";
        }
        if (lower.contains("qwen")) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        if (lower.contains("gpt") || lower.contains("o1") || lower.contains("o3")) {
            return "https://api.openai.com/v1";
        }
        if (lower.contains("claude")) {
            return "https://api.anthropic.com/v1";
        }
        return null;
    }

    /**
     * 解析 Base URL：优先用户输入 → 模型名推断 → 平台默认值
     */
    private String resolveBaseUrl(String provider, String userBaseUrl, String modelName) {
        if (userBaseUrl != null && !userBaseUrl.isEmpty()) {
            return userBaseUrl;
        }
        // custom 提供商：尝试从模型名推断
        if ("custom".equals(provider)) {
            String inferred = inferBaseUrlFromModel(modelName);
            if (inferred != null) {
                return inferred;
            }
        }
        // 其他情况使用平台默认值
        return ApiKeyVO.getDefaultBaseUrl(provider);
    }

    /**
     * 获取用户当前激活的 API Key（解密后，供AIService使用）
     * 返回 null 表示用户没有自定义Key，应使用系统默认配置
     */
    public UserApiKey getActiveDecryptedKey(Long userId) {
        UserApiKey key = apiKeyMapper.findActiveByUserId(userId);
        if (key == null) {
            return null;
        }
        // 复制一份解密后的（不修改数据库实体）
        UserApiKey decrypted = new UserApiKey();
        decrypted.setId(key.getId());
        decrypted.setUserId(key.getUserId());
        decrypted.setProvider(key.getProvider());
        decrypted.setApiKeyEncrypted(aesUtils.decrypt(key.getApiKeyEncrypted()));
        if (key.getSecretKeyEncrypted() != null) {
            decrypted.setSecretKeyEncrypted(aesUtils.decrypt(key.getSecretKeyEncrypted()));
        }
        decrypted.setBaseUrl(key.getBaseUrl());
        decrypted.setModelName(key.getModelName());
        decrypted.setIsActive(key.getIsActive());
        decrypted.setIsValid(key.getIsValid());
        return decrypted;
    }

    /**
     * 实体转VO（脱敏）
     */
    private ApiKeyVO toVO(UserApiKey entity) {
        if (entity == null) return null;
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(entity.getId());
        vo.setProvider(entity.getProvider());
        vo.setProviderLabel(ApiKeyVO.getProviderLabel(entity.getProvider()));
        vo.setApiKeyMasked(entity.getApiKeyEncrypted() != null
                ? AESUtils.mask("sk-****-****-****")
                : "未设置");
        vo.setHasSecretKey(entity.getSecretKeyEncrypted() != null
                && !entity.getSecretKeyEncrypted().isEmpty());
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setModelName(entity.getModelName());
        vo.setIsActive(entity.getIsActive() != null && entity.getIsActive() == 1);
        vo.setIsValid(entity.getIsValid() != null && entity.getIsValid() == 1);
        vo.setLastValidatedAt(entity.getLastValidatedAt());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        vo.setAvailableModels(ApiKeyVO.getModelsForProvider(entity.getProvider()));
        return vo;
    }
}
