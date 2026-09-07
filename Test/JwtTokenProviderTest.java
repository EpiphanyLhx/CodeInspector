package com.codeinspector.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenProvider 单元测试
 * 覆盖：Token 生成、解析、用户ID/角色提取、过期校验
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        // 通过反射注入 @Value 字段
        ReflectionTestUtils.setField(jwtTokenProvider, "secret",
                "dGVzdC1zZWNyZXQta2V5LWZvci1qdW5pdC10ZXN0LW9ubHktMTIzNDU2");
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", 3600000L); // 1小时
    }

    @Test
    @DisplayName("生成Token后应能正确解析出用户ID和用户名")
    void generateToken_shouldParseCorrectly() {
        String token = jwtTokenProvider.generateToken(1001L, "zhangsan", "ADMIN");

        assertNotNull(token, "生成的Token不应为空");
        assertFalse(token.isEmpty(), "生成的Token不应为空字符串");

        Claims claims = jwtTokenProvider.parseToken(token);
        assertEquals(1001L, claims.get("userId", Long.class), "用户ID应一致");
        assertEquals("zhangsan", claims.getSubject(), "用户名应一致");
        assertEquals("ADMIN", claims.get("role", String.class), "角色应一致");
    }

    @Test
    @DisplayName("不同用户生成的Token应不同")
    void generateToken_differentUsers_shouldProduceDifferentTokens() {
        String token1 = jwtTokenProvider.generateToken(1L, "user1", "USER");
        String token2 = jwtTokenProvider.generateToken(2L, "user2", "ADMIN");

        assertNotEquals(token1, token2, "不同用户的Token不应相同");
    }

    @Test
    @DisplayName("Token应包含签发时间且在过期时间之前")
    void generateToken_shouldHaveValidTimestamps() {
        long before = System.currentTimeMillis();
        String token = jwtTokenProvider.generateToken(1L, "test", "USER");
        long after = System.currentTimeMillis();

        Claims claims = jwtTokenProvider.parseToken(token);
        long issuedAt = claims.getIssuedAt().getTime();
        long expiration = claims.getExpiration().getTime();

        assertTrue(issuedAt >= before && issuedAt <= after, "签发时间应在生成时间范围内");
        assertEquals(3600000L, expiration - issuedAt, "有效期应为1小时");
    }

    @Test
    @DisplayName("已过期的Token应抛出异常")
    void parseToken_expiredToken_shouldThrowException() {
        // 设置极短过期时间
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", 1L);
        String token = jwtTokenProvider.generateToken(1L, "test", "USER");

        // 等待过期
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThrows(Exception.class, () -> jwtTokenProvider.parseToken(token),
                "过期Token解析应抛出异常");
    }

    @Test
    @DisplayName("validateToken对有效Token应返回true")
    void validateToken_validToken_shouldReturnTrue() {
        String token = jwtTokenProvider.generateToken(1L, "test", "USER");
        assertTrue(jwtTokenProvider.validateToken(token), "有效Token应通过校验");
    }

    @Test
    @DisplayName("validateToken对非法字符串应返回false")
    void validateToken_invalidToken_shouldReturnFalse() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.string"),
                "非法Token应校验失败");
        assertFalse(jwtTokenProvider.validateToken(""), "空字符串应校验失败");
        assertFalse(jwtTokenProvider.validateToken(null), "null应校验失败");
    }

    @Test
    @DisplayName("getUserId和getRole应正确提取声明")
    void getUserIdAndRole_shouldExtractCorrectly() {
        String token = jwtTokenProvider.generateToken(555L, "tester", "DEVELOPER");

        assertEquals(555L, jwtTokenProvider.getUserId(token), "getUserId应返回555");
        assertEquals("DEVELOPER", jwtTokenProvider.getRole(token), "getRole应返回DEVELOPER");
    }
}
