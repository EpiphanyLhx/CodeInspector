package com.codeinspector.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AESUtils 单元测试
 * 覆盖：加密、解密、空值处理、加密后密文与原文不同、往返一致性
 */
class AESUtilsTest {

    private AESUtils aesUtils;

    @BeforeEach
    void setUp() {
        aesUtils = new AESUtils();
        // 16字节密钥（AES-128），Base64编码
        ReflectionTestUtils.setField(aesUtils, "secret",
                "MTIzNDU2Nzg5MDEyMzQ1Ng=="); // "1234567890123456"
    }

    @Test
    @DisplayName("普通字符串加密后应能正确解密还原")
    void encryptAndDecrypt_normalString_shouldRoundTrip() {
        String original = "sk-abc123def456ghi789";
        String encrypted = aesUtils.encrypt(original);
        String decrypted = aesUtils.decrypt(encrypted);

        assertNotNull(encrypted, "加密结果不应为空");
        assertNotEquals(original, encrypted, "密文不应与明文相同");
        assertEquals(original, decrypted, "解密后应还原原文");
    }

    @Test
    @DisplayName("含中文和特殊字符的字符串应正常加密解密")
    void encryptAndDecrypt_chineseAndSpecialChars_shouldRoundTrip() {
        String original = "密码：Pass@#$%^&*()_+中文测试🎉";
        String encrypted = aesUtils.encrypt(original);
        String decrypted = aesUtils.decrypt(encrypted);

        assertEquals(original, decrypted, "含特殊字符的字符串应正确还原");
    }

    @Test
    @DisplayName("相同明文每次加密结果应不同（随机IV）")
    void encrypt_samePlaintext_shouldProduceDifferentCiphertext() {
        String plaintext = "repeated-secret-key";
        String enc1 = aesUtils.encrypt(plaintext);
        String enc2 = aesUtils.encrypt(plaintext);

        // AES/CBC/PKCS5Padding 使用随机IV，相同明文每次密文不同
        assertNotEquals(enc1, enc2, "相同明文每次加密结果应不同");
        // 但都能解密回原文
        assertEquals(plaintext, aesUtils.decrypt(enc1));
        assertEquals(plaintext, aesUtils.decrypt(enc2));
    }

    @Test
    @DisplayName("空字符串应正常加密解密")
    void encryptAndDecrypt_emptyString_shouldWork() {
        String encrypted = aesUtils.encrypt("");
        String decrypted = aesUtils.decrypt(encrypted);
        assertEquals("", decrypted, "空字符串应正确还原");
    }

    @Test
    @DisplayName("null输入应返回null而非抛异常")
    void encryptAndDecrypt_nullInput_shouldReturnNull() {
        assertNull(aesUtils.encrypt(null), "encrypt(null)应返回null");
        assertNull(aesUtils.decrypt(null), "decrypt(null)应返回null");
    }

    @Test
    @DisplayName("长文本（>16字节）应正确分块加密解密")
    void encryptAndDecrypt_longText_shouldRoundTrip() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("这是一段用于测试AES加密的长文本内容，包含多个分块。");
        }
        String original = sb.toString();
        String encrypted = aesUtils.encrypt(original);
        String decrypted = aesUtils.decrypt(encrypted);

        assertEquals(original.length(), decrypted.length(), "长文本长度应一致");
        assertEquals(original, decrypted, "长文本应正确还原");
    }

    @Test
    @DisplayName("篡改密文后解密应抛出异常")
    void decrypt_tamperedCiphertext_shouldThrowException() {
        String encrypted = aesUtils.encrypt("sensitive-data");
        // 篡改密文
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThrows(Exception.class, () -> aesUtils.decrypt(tampered),
                "篡改后的密文解密应抛出异常");
    }
}
