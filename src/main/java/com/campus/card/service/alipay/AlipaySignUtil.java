package com.campus.card.service.alipay;

import com.campus.card.common.BizException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @Description 支付宝签名 / 验签工具（纯 JDK 实现的手写版）。
 *              ★ 主路径已经换成官方 SDK 了（见 config/AlipaySdkConfig + AlipayPayServiceImpl）：
 *                「验签要不要剔 sign_type」「参数放查询串还是 POST 体」这些事现在由 SDK 保证。
 *                本类保留下来当【对照实现】，用来钉住「我们对规则的理解」：
 *                单测 AlipayNotifyTest 用它按真实规则签报文，再由 SDK 的 rsaCheckV1 验回来 ——
 *                两边规则只要差一点点，单测立刻红。规则本身没错，错的是当年只写了一份、上下行共用。
 *              规则：只剔除 sign 自己、剔除空值、按参数名升序拼成 k=v&k=v，
 *              再用 SHA256withRSA 签名 —— 支付宝管这套叫 RSA2。
 *              ★ sign_type 是要参与签名的，别顺手把它也剔掉（踩过：网关回 invalid-signature）。
 * @Author u
 * @Date 2026/10/10
 */
public final class AlipaySignUtil {

    /** RSA2 就是 SHA256withRSA，只是支付宝给它取了另一个名字 */
    private static final String ALGORITHM = "SHA256withRSA";
    private static final String KEY_ALGORITHM = "RSA";

    /**
     * 只有 sign 自己不参与拼串 —— 它是被验的那个对象。
     * ★ sign_type 看着像个「标签」，但【上行签名】时它必须参与：官方 SDK 的 getSignContent()
     *   输出里就带着 `&sign_type=RSA2&`。剔掉它，网关会回 invalid-signature（实测）。
     */
    private static final String SIGN = "sign";

    /**
     * ★★ 上行的规则不能照搬到下行：验证【支付宝回给我们的】报文时，sign_type 要一起剔掉。
     *   官方 SDK 的 rsaCheckV1() 里就两行：params.remove("sign"); params.remove("sign_type");
     *   实测（拿支付宝真实回过的一次 return 参数 + 它自己签的 sign）：
     *     带 sign_type 拼串 → verify = false
     *     不带 sign_type 拼串 → verify = true
     *   同一份报文、同一个 sign、同一把公钥，只差这一个参数。
     */
    private static final String SIGN_TYPE = "sign_type";

    private AlipaySignUtil() {
    }

    /**
     * 待签名字符串（上行：我们发出去的请求）。
     * ★ 值是原文，不做 URL 编码 —— URL 编码是「提交表单」那一刻的事，不是「签名」这一刻的事。
     */
    public static String signContent(Map<String, String> params) {
        return content(params, false);
    }

    /**
     * 待验签字符串（下行：支付宝回给我们的通知 / 回跳报文）。
     * ★ 和 signContent 只差一件事：sign_type 也不参与。
     *   少剔这一个参数，表现是「验签失败 → 回 failure → 支付宝重发」，钱在支付宝那边已经扣了。
     */
    public static String verifyContent(Map<String, String> params) {
        return content(params, true);
    }

    private static String content(Map<String, String> params, boolean skipSignType) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            String value = params.get(key);
            if (SIGN.equals(key) || (skipSignType && SIGN_TYPE.equals(key))) {
                continue;
            }
            // ★ 空值不参与拼接：少拼一个空参数和拼一个 "k=" 是两个不同的串
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=').append(value);
        }
        return sb.toString();
    }

    /** 用「应用私钥」签名，返回 Base64 */
    public static String sign(Map<String, String> params, String merchantPrivateKey) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey(merchantPrivateKey));
            signature.update(signContent(params).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new BizException("支付宝签名失败：" + e.getMessage());
        }
    }

    /**
     * 用「支付宝公钥」验签。
     * ★ 这里任何异常都算「没验过」，不往外抛 —— 验签失败是一个业务结果（回 failure 让支付宝重发），
     *   不是技术异常。签名格式不对、公钥粘错、算法不匹配，在这里是同一件事。
     * ★★ 注意用的是 verifyContent（剔 sign + sign_type），不是 sign() 用的 signContent（只剔 sign）。
     */
    public static boolean verify(Map<String, String> params, String alipayPublicKey) {
        String sign = params.get(SIGN);
        if (sign == null || sign.isEmpty()) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey(alipayPublicKey));
            signature.update(verifyContent(params).getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /** 应用私钥：PKCS#8 的 base64（支付宝密钥工具给的就是这个格式） */
    private static PrivateKey privateKey(String key) throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(clean(key));
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    /** 支付宝公钥：X.509 的 base64 */
    private static PublicKey publicKey(String key) throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(clean(key));
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(bytes));
    }

    /** 从网页上复制一整行 base64 时，最容易带上换行和空格，解码前先清掉 */
    private static String clean(String key) {
        return key == null ? "" : key.replaceAll("\\s", "");
    }
}
