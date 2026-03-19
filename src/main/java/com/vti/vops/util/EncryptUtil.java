package com.vti.vops.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 加密/解密，用于 SSH 密码、私钥等敏感信息存储。
 * 加密结果带固定前缀，仅对带前缀的串做 Base64 解码，避免对明文解码导致异常。
 */
@Slf4j
public final class EncryptUtil {

    private static final String ALG = "AES/CBC/PKCS5Padding";
    private static final int IV_LEN = 16;
    /** 加密结果前缀，仅对带此前缀的串做解密，其余原样返回 */
    public static final String PREFIX = "AES:";
    private static final int MAX_DECRYPT_LEN = 64 * 1024;

    private final byte[] keyBytes;

    public EncryptUtil(String key) {
        if (key == null || key.length() < 16) {
            throw new IllegalArgumentException("AES key must be at least 16 bytes");
        }
        this.keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length > 32) {
            throw new IllegalArgumentException("AES key max 32 bytes");
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            byte[] key = keyBytes.length == 16 ? keyBytes : java.util.Arrays.copyOf(keyBytes, 32);
            SecretKeySpec skey = new SecretKeySpec(key, "AES");
            byte[] iv = new byte[IV_LEN];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.ENCRYPT_MODE, skey, new IvParameterSpec(iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(enc, 0, combined, iv.length, enc.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encrypt failed", e);
            throw new RuntimeException(e);
        }
    }

    /** 是否为本工具加密后的串（仅认带前缀），避免把用户明文密码当密文导致不加密存储、解密时得到 null */
    public boolean isEncrypted(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.startsWith(PREFIX);
    }

    /** 仅对带 {@link #PREFIX} 的串或兼容的旧格式做 Base64+AES 解密；失败返回 null，避免把密文当明文用 */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return encrypted;
        String base64;
        if (encrypted.startsWith(PREFIX)) {
            base64 = encrypted.substring(PREFIX.length());
        } else {
            if (encrypted.length() > MAX_DECRYPT_LEN) return encrypted;
            base64 = encrypted;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(base64);
            if (combined.length <= IV_LEN) {
                log.warn("Decrypt failed: data too short (truncated?)");
                return null;
            }
            byte[] key = keyBytes.length == 16 ? keyBytes : java.util.Arrays.copyOf(keyBytes, 32);
            SecretKeySpec skey = new SecretKeySpec(key, "AES");
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(combined, 0, iv, 0, IV_LEN);
            byte[] enc = new byte[combined.length - IV_LEN];
            System.arraycopy(combined, IV_LEN, enc, 0, enc.length);
            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.DECRYPT_MODE, skey, new IvParameterSpec(iv));
            return new String(cipher.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Decrypt failed (invalid/corrupt or truncated data): {}", e.getMessage());
            return null;
        }
    }
}
