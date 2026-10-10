package com.campus.card.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Description 支付宝官方 SDK 的客户端（单例 Bean）。
 *              ★ 为什么做成 Bean，而不是在 Service 里每次 new：
 *                DefaultAlipayClient 内部持有一个 HTTP 客户端，官方把它当可复用组件用；
 *                每来一个请求就 new 一个，等于每次重开一个连接池。
 *              ★ 为什么把 charset / format / signType 写死在这里：
 *                这三样不是「随手填」的参数，它们决定报文长什么样 ——
 *                charset 决定验签时按哪个字符集取字节（必须和报文里的 charset 一致），
 *                format 决定网关回什么格式，signType=RSA2 才是 SHA256withRSA（不写就是老的 SHA1）。
 * @Author u
 * @Date 2026/10/10
 */
@Configuration
public class AlipaySdkConfig {

    /** RSA2 就是 SHA256withRSA，支付宝给它取了另一个名字（和 AlipayPayServiceImpl 里那个值必须一致） */
    public static final String SIGN_TYPE_RSA2 = "RSA2";
    /** ★ 必须是 UTF-8：验签取字节、表单编码都用它 */
    public static final String CHARSET_UTF8 = "UTF-8";
    /** 网关返回格式 */
    public static final String FORMAT_JSON = "json";

    @Bean
    public AlipayClient alipayClient(
            @Value("${campus.alipay.gateway-url}") String gatewayUrl,
            @Value("${campus.alipay.app-id}") String appId,
            @Value("${campus.alipay.merchant-private-key}") String merchantPrivateKey,
            @Value("${campus.alipay.alipay-public-key}") String alipayPublicKey) {
        // ★ 参数顺序是有讲究的，别按感觉排：
        //   (网关地址, APPID, 应用私钥, format, charset, 支付宝公钥, 签名算法)
        return new DefaultAlipayClient(
                gatewayUrl, appId, merchantPrivateKey, FORMAT_JSON, CHARSET_UTF8, alipayPublicKey, SIGN_TYPE_RSA2);
    }
}
