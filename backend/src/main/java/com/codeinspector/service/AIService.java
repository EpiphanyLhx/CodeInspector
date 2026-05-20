package com.codeinspector.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI服务 - 对接通义千问/文心一言API
 * 核心：通过Prompt约束大模型强制返回标准JSON
 */
@Slf4j
@Service
public class AIService {

    @Value("${ai.provider:tongyi}")
    private String provider;

    @Value("${ai.tongyi.api-key:}")
    private String tongyiApiKey;

    @Value("${ai.tongyi.model:qwen-max}")
    private String tongyiModel;

    @Value("${ai.wenxin.api-key:}")
    private String wenxinApiKey;

    @Value("${ai.wenxin.secret-key:}")
    private String wenxinSecretKey;

    @Value("${ai.wenxin.model:ernie-4.0}")
    private String wenxinModel;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

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
     * 审查代码片段
     */
    public JSONObject reviewCode(String codeContent, String fileName) {
        String userPrompt = String.format(
                "请审查以下文件 %s 的代码:\n\n```\n%s\n```", fileName, codeContent);

        String response = switch (provider) {
            case "tongyi" -> callTongyiQwen(REVIEW_SYSTEM_PROMPT, userPrompt);
            case "wenxin" -> callWenxin(REVIEW_SYSTEM_PROMPT, userPrompt);
            default -> throw new IllegalArgumentException("不支持的AI提供商: " + provider);
        };

        return parseAIResponse(response);
    }

    /**
     * 审查代码切片（用于突破Token限制）
     */
    public JSONObject reviewCodeChunk(String chunkContent, String elementName, String elementType, String fileName) {
        String userPrompt = String.format(
                "请审查文件 %s 中的%s %s 的代码片段:\n\n```\n%s\n```",
                fileName, elementType, elementName, chunkContent);

        String response = switch (provider) {
            case "tongyi" -> callTongyiQwen(REVIEW_SYSTEM_PROMPT, userPrompt);
            case "wenxin" -> callWenxin(REVIEW_SYSTEM_PROMPT, userPrompt);
            default -> throw new IllegalArgumentException("不支持的AI提供商: " + provider);
        };

        return parseAIResponse(response);
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

    // ================ 通义千问 API ================

    private String callTongyiQwen(String systemPrompt, String userPrompt) {
        JSONObject body = new JSONObject();
        body.put("model", tongyiModel);

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

        Request request = new Request.Builder()
                .url("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")
                .header("Authorization", "Bearer " + tongyiApiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() != null ? response.body().string() : "";
                log.error("通义千问API调用失败: {} {}", response.code(), errBody);
                throw new RuntimeException("AI服务调用失败: " + response.code());
            }
            JSONObject respJson = JSON.parseObject(response.body().string());
            return respJson.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (IOException e) {
            log.error("通义千问API网络错误: ", e);
            throw new RuntimeException("AI服务网络异常: " + e.getMessage());
        }
    }

    // ================ 文心一言 API ================

    private String accessToken; // 缓存access_token
    private long tokenExpireTime;

    private String getWenxinAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }
        String url = String.format(
                "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=%s&client_secret=%s",
                wenxinApiKey, wenxinSecretKey);

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            JSONObject json = JSON.parseObject(response.body().string());
            accessToken = json.getString("access_token");
            int expiresIn = json.getIntValue("expires_in", 2592000);
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
            return accessToken;
        } catch (IOException e) {
            log.error("获取文心一言AccessToken失败: ", e);
            throw new RuntimeException("获取文心一言Token失败");
        }
    }

    private String callWenxin(String systemPrompt, String userPrompt) {
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

        String url = String.format(
                "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions_pro?access_token=%s",
                getWenxinAccessToken());

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

    /**
     * 解析AI返回的JSON - 处理各种异常格式
     */
    private JSONObject parseAIResponse(String response) {
        if (response == null || response.isBlank()) {
            log.warn("AI返回空内容");
            return createEmptyResult();
        }
        try {
            // 去除可能的markdown代码块包裹
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
