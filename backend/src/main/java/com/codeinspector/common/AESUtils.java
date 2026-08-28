package com.codeinspector.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * AES加密工具类 - 用于敏感信息（如API Key）的加密存储
 */
@Slf4j
@Component
public class AESUtils {

    private final SecretKeySpec secretKey;

    public AESUtils(@Value("${security.aes-secret:CodeInspector-AES-Secret-Key-2024}") String secret) {
        try {
            // 使用SHA-256确保密钥长度为256位
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] key = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            log.error("AES密钥初始化失败", e);
            throw new RuntimeException("AES密钥初始化失败", e);
        }
    }

    /**
     * 加密
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("AES加密失败", e);
            throw new RuntimeException("数据加密失败", e);
        }
    }

    /**
     * 解密
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES解密失败", e);
            throw new RuntimeException("数据解密失败", e);
        }
    }

    /**
     * 掩码显示 - 只显示前4位和后4位，中间用*代替
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return "***";
        }
        if (text.length() <= 8) {
            return text.charAt(0) + "****" + text.charAt(text.length() - 1);
        }
        return text.substring(0, 4) + "****" + text.substring(text.length() - 4);
    }
}
