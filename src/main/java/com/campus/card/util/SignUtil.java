package com.campus.card.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 通知报文的签名工具（HMAC-SHA256）。
 *
 * @author 87
 * @date 2026/10/04
 */
public class SignUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private SignUtil() {
    }

    /**
     * 协会sha256
     *
     * @param secret  双方约定的密钥
     * @param content 待签内容
     * @return {@link String } 小写十六进制字符串
     */
    public static String hmacSha256(String secret, String content) {
        try {
            //1. 获取算法实例，无密钥，未就绪
            Mac mac = Mac.getInstance(ALGORITHM);
            //2. 将密钥字符串转为字节，包装成密钥对象，初始化mac，装入密钥，就绪
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            //3. 计算HMAC签名
            byte[] raw = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[raw.length * 2];
            final char[] DIGITS = "0123456789abcdef".toCharArray();
            for (int i = 0; i < raw.length; i++) {
                hex[i * 2] = DIGITS[(raw[i] >> 4) & 0x0f];
                hex[i * 2 + 1] = DIGITS[raw[i] & 0x0f];
            }
            return new String(hex);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 签名失败", e);
        }

    }

    public static boolean verify(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.length() != actual.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < expected.length(); i++) {
            diff |= expected.charAt(i) ^ actual.charAt(i);
        }
        return diff == 0;
    }

}
