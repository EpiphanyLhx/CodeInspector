package com.codeinspector.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.codeinspector.model.entity.UserApiKey;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * AI服务 - 对接通义千问/文心一言/OpenAI/自定义API
 * 支持系统默认配置 + 用户自定义API Key动态切换
 */
@Slf4j
@Service
public class AIService {

    @Value("${ai.provider:tongyi}")
    private String defaultProvider;

    @Value("${ai.tongyi.api-key:}")
    private String defaultTongyiApiKey;

    @Value("${ai.tongyi.model:qwen-max}")
    private String defaultTongyiModel;

    @Value("${ai.wenxin.api-key:}")
    private String defaultWenxinApiKey;

    @Value("${ai.wenxin.secret-key:}")
    private String defaultWenxinSecretKey;

    @Value("${ai.wenxin.model:ernie-4.0}")
    private String defaultWenxinModel;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    // 文心一言 access_token缓存 (key: clientId, value: {token, expireTime})
    private final Map<String, TokenCache> wenxinTokenCache = new ConcurrentHashMap<>();

    private static class TokenCache {
        String token;
        long expireTime;
        TokenCache(String token, long expireTime) {
            this.token = token;
            this.expireTime = expireTime;
        }
    }

    private static final String REVIEW_SYSTEM_PROMPT =
            "你是一个专业的代码审查专家。请严格审查提供的代码片段，并必须按以下JSON格式返回结果。\n" +
            "不要返回JSON之外的任何内容，不要用markdown代码块包裹。\n\n" +
            "返回格式:\n" +
            "{\n" +
            "  \"issues\": [\n" +
            "    {\n" +
            "      \"lineStart\": 行号(整数),\n" +
            "      \"lineEnd\": 结束行号(整数, 如果只有一行则与lineStart相同),\n" +
            "      \"severity\": \"CRITICAL|MAJOR|MINOR|INFO\",\n" +
            "      \"category\": \"SECURITY|BUG|CODE_STYLE|PERFORMANCE|BEST_PRACTICE\",\n" +
            "      \"title\": \"简短的问题标题\",\n" +
            "      \"description\": \"详细的问题描述\",\n" +
            "      \"suggestion\": \"具体的修复建议\",\n" +
            "      \"fixedCode\": \"修复后的代码片段\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"summary\": \"整体代码质量评估，50字以内\"\n" +
            "}\n\n" +
            "审查标准:\n" +
            "1. SECURITY: SQL注入、XSS、CSRF、硬编码密码、不安全的反序列化、路径遍历等\n" +
            "2. BUG: 空指针风险、资源泄漏、并发问题、逻辑错误、边界条件\n" +
            "3. CODE_STYLE: 命名规范、代码格式、魔法数字、过长方法\n" +
            "4. PERFORMANCE: 不必要的对象创建、字符串拼接、N+1查询、未使用缓存\n" +
            "5. BEST_PRACTICE: 异常处理不当、缺乏日志、过时API使用、设计模式滥用\n\n" +
            "若未发现问题返回: {\"issues\":[],\"summary\":\"未发现明显问题\"}";

    /**
     * 风格画像注入片段。当用户启用"按我的代码风格审查"时，追加到 system prompt 之后，
     * 约束 AI 的 suggestion / fixedCode 遵循用户既有风格，且不把用户既有风格习惯报为问题。
     */
    private static final String STYLE_GUIDE_TEMPLATE =
            "\n\n【用户代码风格画像】\n%s\n\n" +
            "【风格一致性要求（重要）】\n" +
            "0. 首要原则：SECURITY（安全）、BUG（Bug）、PERFORMANCE（性能）类问题必须照常、完整地报告，" +
            "绝对不能因为代码风格画像而省略或弱化这些问题。风格偏好只影响 CODE_STYLE 类问题和修复代码的写法。\n" +
            "1. 对于 CODE_STYLE 类问题：仅当用户的写法本身没有功能/可读性问题、且与上方风格画像明确一致时" +
            "（例如缩进是4空格还是Tab、大括号同行还是换行、变量命名用camelCase等），才不报告；" +
            "凡是影响可读性、可维护性或存在歧义的风格问题（如魔法数字、过长方法、命名不规范、缺少必要注释等）仍需报告。\n" +
            "2. suggestion（修复建议）中给出的代码写法必须遵循上方风格画像：保持一致的缩进、大括号位置、命名风格、注释风格和日志方式。\n" +
            "3. fixedCode（修复后代码）必须与用户现有代码风格保持一致，不要引入用户未使用的框架、注解或编码习惯；" +
            "除修复所必需的改动外，不要随意改动周边代码的风格。\n" +
            "4. fixedCode 只包含修复所涉及的最小代码片段，行号保持与被审查代码一致。";

    // ================ 公开方法 ================

    /**
     * 审查代码片段（使用系统默认配置）
     */
    public JSONObject reviewCode(String codeContent, String fileName) {
        return reviewCode(codeContent, fileName, null, null);
    }

    /**
     * 审查代码片段（支持用户自定义API Key）
     */
    public JSONObject reviewCode(String codeContent, String fileName, UserApiKey userKey) {
        return reviewCode(codeContent, fileName, userKey, null);
    }

    /**
     * 审查代码片段（支持用户自定义API Key + 代码风格画像）
     */
    public JSONObject reviewCode(String codeContent, String fileName, UserApiKey userKey, String styleProfile) {
        String userPrompt = String.format(
                "请审查以下文件 %s 的代码:\n\n```\n%s\n```", fileName, codeContent);
        String systemPrompt = buildSystemPrompt(styleProfile);
        String response = callAI(systemPrompt, userPrompt, userKey);
        return parseAIResponse(response);
    }

    /**
     * 审查代码切片（使用系统默认配置）
     */
    public JSONObject reviewCodeChunk(String chunkContent, String elementName, String elementType, String fileName) {
        return reviewCodeChunk(chunkContent, elementName, elementType, fileName, null, null);
    }

    /**
     * 审查代码切片（支持用户自定义API Key）
     */
    public JSONObject reviewCodeChunk(String chunkContent, String elementName, String elementType,
                                       String fileName, UserApiKey userKey) {
        return reviewCodeChunk(chunkContent, elementName, elementType, fileName, userKey, null);
    }

    /**
     * 审查代码切片（支持用户自定义API Key + 代码风格画像）
     */
    public JSONObject reviewCodeChunk(String chunkContent, String elementName, String elementType,
                                       String fileName, UserApiKey userKey, String styleProfile) {
        String userPrompt = String.format(
                "请审查文件 %s 中的%s %s 的代码片段:\n\n```\n%s\n```",
                fileName, elementType, elementName, chunkContent);
        String systemPrompt = buildSystemPrompt(styleProfile);
        String response = callAI(systemPrompt, userPrompt, userKey);
        return parseAIResponse(response);
    }

    /**
     * 根据是否提供风格画像，构建最终 system prompt
     */
    private String buildSystemPrompt(String styleProfile) {
        if (styleProfile == null || styleProfile.isBlank()) {
            return REVIEW_SYSTEM_PROMPT;
        }
        return REVIEW_SYSTEM_PROMPT + String.format(STYLE_GUIDE_TEMPLATE, styleProfile);
    }

    /**
     * 计算Token数量（粗略估算：1token ≈ 2个中文字符 ≈ 4个英文字符）
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) Math.ceil(chineseChars / 2.0 + otherChars / 4.0);
    }

    // ================ AI调用分发 ================

    /**
     * 根据配置调用对应的AI API
     */
    private String callAI(String systemPrompt, String userPrompt, UserApiKey userKey) {
        // 如果用户有自定义Key，优先使用
        if (userKey != null && userKey.getApiKeyEncrypted() != null
                && !userKey.getApiKeyEncrypted().isEmpty()) {
            return callWithUserKey(systemPrompt, userPrompt, userKey);
        }
        // 否则使用系统默认配置
        return callWithDefault(systemPrompt, userPrompt);
    }

    /**
     * 使用用户自定义Key调用
     */
    private String callWithUserKey(String systemPrompt, String userPrompt, UserApiKey key) {
        String apiKey = key.getApiKeyEncrypted(); // 已经是解密后的
        String secretKey = key.getSecretKeyEncrypted(); // 已经是解密后的
        String model = key.getModelName();
        String baseUrl = key.getBaseUrl();

        return switch (key.getProvider()) {
            case "tongyi" -> callOpenAICompatible(systemPrompt, userPrompt, model, apiKey,
                    baseUrl != null && !baseUrl.isEmpty() ? baseUrl
                            : "https://dashscope.aliyuncs.com/compatible-mode/v1");
            case "openai" -> callOpenAICompatible(systemPrompt, userPrompt, model, apiKey,
                    baseUrl != null && !baseUrl.isEmpty() ? baseUrl
                            : "https://api.openai.com/v1");
            case "custom" -> {
                String resolved = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl
                        : inferCustomBaseUrl(model);
                if (resolved == null || resolved.isEmpty()) {
                    throw new IllegalArgumentException(
                            "自定义API缺少端点地址，请在「个人信息 → API密钥」中设置Base URL");
                }
                yield callOpenAICompatible(systemPrompt, userPrompt, model, apiKey, resolved);
            }
            case "wenxin" -> callWenxinWithKey(systemPrompt, userPrompt, model, apiKey, secretKey);
            default -> throw new IllegalArgumentException("不支持的AI提供商: " + key.getProvider());
        };
    }

    /**
     * 从模型名推断自定义API的base URL
     */
    private String inferCustomBaseUrl(String modelName) {
        if (modelName == null) return null;
        String lower = modelName.toLowerCase();
        if (lower.contains("deepseek")) return "https://api.deepseek.com/v1";
        if (lower.contains("glm") || lower.contains("chatglm")) return "https://open.bigmodel.cn/api/paas/v4";
        if (lower.contains("moonshot") || lower.contains("kimi")) return "https://api.moonshot.cn/v1";
        if (lower.contains("qwen")) return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        if (lower.contains("gpt") || lower.contains("o1") || lower.contains("o3"))
            return "https://api.openai.com/v1";
        if (lower.contains("claude")) return "https://api.anthropic.com/v1";
        return null;
    }

    /**
     * 使用系统默认配置调用
     */
    private String callWithDefault(String systemPrompt, String userPrompt) {
        return switch (defaultProvider) {
            case "tongyi" -> callOpenAICompatible(systemPrompt, userPrompt, defaultTongyiModel,
                    defaultTongyiApiKey, "https://dashscope.aliyuncs.com/compatible-mode/v1");
            case "wenxin" -> callWenxinWithKey(systemPrompt, userPrompt, defaultWenxinModel,
                    defaultWenxinApiKey, defaultWenxinSecretKey);
            default -> throw new IllegalArgumentException("不支持的AI提供商: " + defaultProvider);
        };
    }

    // ================ OpenAI 兼容接口（通义千问/OpenAI/自定义） ================

    private String callOpenAICompatible(String systemPrompt, String userPrompt,
                                         String model, String apiKey, String baseUrl) {
        JSONObject body = new JSONObject();
        body.put("model", model);

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        body.put("messages", messages);
        body.put("temperature", 0.1);
        body.put("max_tokens", 4096);

        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.error("AI API调用失败 [{}] {}: {}", model, response.code(), errBody);
                throw new RuntimeException("AI服务调用失败: " + response.code());
            }
            JSONObject respJson = JSON.parseObject(response.body().string());
            return respJson.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (IOException e) {
            log.error("AI API网络错误 [{}]: ", model, e);
            throw new RuntimeException("AI服务网络异常: " + e.getMessage());
        }
    }

    // ================ 文心一言 API ================

    private String getWenxinAccessToken(String apiKey, String secretKey) {
        String cacheKey = apiKey;
        TokenCache cache = wenxinTokenCache.get(cacheKey);
        if (cache != null && System.currentTimeMillis() < cache.expireTime) {
            return cache.token;
        }

        String url = String.format(
                "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=%s&client_secret=%s",
                apiKey, secretKey);

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            JSONObject json = JSON.parseObject(response.body().string());
            String token = json.getString("access_token");
            int expiresIn = json.getIntValue("expires_in", 2592000);
            wenxinTokenCache.put(cacheKey, new TokenCache(token,
                    System.currentTimeMillis() + (expiresIn - 60) * 1000L));
            return token;
        } catch (IOException e) {
            log.error("获取文心一言AccessToken失败: ", e);
            throw new RuntimeException("获取文心一言Token失败");
        }
    }

    private String callWenxinWithKey(String systemPrompt, String userPrompt,
                                      String model, String apiKey, String secretKey) {
        String accessToken = getWenxinAccessToken(apiKey, secretKey);

        JSONObject body = new JSONObject();
        body.put("system", systemPrompt);

        JSONArray messages = new JSONArray();
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", userPrompt);
        messages.add(msg);
        body.put("messages", messages);

        body.put("temperature", 0.1);
        body.put("max_output_tokens", 4096);

        // 根据模型选择不同的endpoint
        String endpoint = getWenxinEndpoint(model);
        String url = String.format("%s?access_token=%s", endpoint, accessToken);

        Request request = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.error("文心一言API调用失败: {} {}", response.code(), errBody);
                throw new RuntimeException("AI服务调用失败: " + response.code());
            }
            JSONObject respJson = JSON.parseObject(response.body().string());
            return respJson.getString("result");
        } catch (IOException e) {
            log.error("文心一言API网络错误: ", e);
            throw new RuntimeException("AI服务网络异常: " + e.getMessage());
        }
    }

    private String getWenxinEndpoint(String model) {
        // 文心一言不同模型的endpoint映射
        if (model.contains("ernie-4.0")) {
            return "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions_pro";
        } else if (model.contains("ernie-3.5")) {
            return "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions";
        } else if (model.contains("speed")) {
            return "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie_speed";
        } else if (model.contains("lite")) {
            return "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/eb-instant";
        } else if (model.contains("tiny")) {
            return "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-tiny-8k";
        }
        return "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions";
    }

    // ================ JSON解析 ================

    /**
     * 解析AI返回的JSON - 处理各种异常格式
     */
    private JSONObject parseAIResponse(String response) {
        if (response == null || response.isBlank()) {
            log.warn("AI返回空内容");
            return createEmptyResult();
        }
        try {
            String cleaned = response.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            if (cleaned.startsWith("{")) {
                int braceEnd = cleaned.lastIndexOf("}");
                if (braceEnd > 0) {
                    cleaned = cleaned.substring(0, braceEnd + 1);
                }
            }

            return JSON.parseObject(cleaned);
        } catch (Exception e) {
            log.warn("AI返回JSON解析失败: {}, 原始内容: {}", e.getMessage(), response);
            return createEmptyResult();
        }
    }

    private JSONObject createEmptyResult() {
        JSONObject result = new JSONObject();
        result.put("issues", new JSONArray());
        result.put("summary", "AI审查解析异常，请重试");
        return result;
    }
}
