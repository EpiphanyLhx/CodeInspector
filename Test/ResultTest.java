package com.codeinspector.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一响应体单元测试
 * 覆盖：success、error、unauthorized、forbidden、字段正确性
 */
class ResultTest {

    @Test
    @DisplayName("success应返回code=200且包含数据")
    void success_shouldReturnCode200WithData() {
        String data = "test-data";
        Result<String> result = Result.success(data);

        assertEquals(200, result.getCode(), "成功响应code应为200");
        assertEquals("success", result.getMessage(), "成功响应message应为success");
        assertEquals(data, result.getData(), "数据应正确携带");
        assertTrue(result.getTimestamp() > 0, "时间戳应大于0");
    }

    @Test
    @DisplayName("success(null)应正常返回data为null")
    void success_withNullData_shouldWork() {
        Result<Void> result = Result.success(null);
        assertEquals(200, result.getCode());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("error应返回code=500且包含错误信息")
    void error_shouldReturnCode500WithMessage() {
        String errorMsg = "数据库连接失败";
        Result<Void> result = Result.error(errorMsg);

        assertEquals(500, result.getCode(), "错误响应code应为500");
        assertEquals(errorMsg, result.getMessage(), "错误信息应正确携带");
        assertNull(result.getData(), "错误响应data应为null");
    }

    @Test
    @DisplayName("error(code, message)应支持自定义错误码")
    void error_withCustomCode_shouldUseGivenCode() {
        Result<Void> result = Result.error(400, "参数校验失败");

        assertEquals(400, result.getCode(), "应使用自定义错误码400");
        assertEquals("参数校验失败", result.getMessage());
    }

    @Test
    @DisplayName("unauthorized应返回code=401")
    void unauthorized_shouldReturnCode401() {
        Result<Void> result = Result.unauthorized();

        assertEquals(401, result.getCode(), "未授权响应code应为401");
        assertEquals("未登录或登录已过期", result.getMessage());
    }

    @Test
    @DisplayName("forbidden应返回code=403且包含原因")
    void forbidden_shouldReturnCode403WithMessage() {
        Result<Void> result = Result.forbidden("需要管理员权限");

        assertEquals(403, result.getCode(), "禁止访问响应code应为403");
        assertEquals("需要管理员权限", result.getMessage());
    }

    @Test
    @DisplayName("时间戳应为当前时间附近")
    void timestamp_shouldBeCurrentTime() {
        long before = System.currentTimeMillis();
        Result<String> result = Result.success("test");
        long after = System.currentTimeMillis();

        assertTrue(result.getTimestamp() >= before && result.getTimestamp() <= after,
                "时间戳应在生成时间范围内");
    }

    @Test
    @DisplayName("无参构造应创建空对象")
    void noArgConstructor_shouldCreateEmptyObject() {
        Result<String> result = new Result<>();
        assertEquals(0, result.getCode());
        assertNull(result.getMessage());
        assertNull(result.getData());
        assertEquals(0, result.getTimestamp());
    }

    @Test
    @DisplayName("全参构造应正确设置所有字段")
    void allArgsConstructor_shouldSetAllFields() {
        Result<String> result = new Result<>(201, "created", "obj", 123456789L);

        assertEquals(201, result.getCode());
        assertEquals("created", result.getMessage());
        assertEquals("obj", result.getData());
        assertEquals(123456789L, result.getTimestamp());
    }
}
