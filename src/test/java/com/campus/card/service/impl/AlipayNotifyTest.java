package com.campus.card.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.DefaultAlipayClient;
import com.campus.card.common.BizException;
import com.campus.card.entity.RechargeOrder;
import com.campus.card.mapper.RechargeOrderMapper;
import com.campus.card.service.PayCallbackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付宝异步通知 handleNotify 的行为测试（不启 Spring 容器，纯 Mockito）。
 *
 * ★ 真实支付宝的报文我们伪造不了（没有它的私钥），所以测试里自己生成一对 RSA 密钥：
 *   公钥当「支付宝公钥」配进服务，私钥用来扮演支付宝签报文 —— 验签自然能过，
 *   于是 handleNotify 后面的分支（金额核对、入账、异常兜底）就都能走到了。
 * ★ 签名和验签都走官方 SDK（rsaSign / rsaCheckV1）。这几个用例钉的是【我们的接线】：
 *   报文怎么读、金额跟谁比、异常回什么、幂等交给谁 —— 不钉 SDK 自己的签名规则（那是它的事）。
 */
class AlipayNotifyTest {

    private static final String APP_ID = "2026000000000000";
    private static final String ORDER_NO = "R20261010999TEST";
    private static final String TRADE_NO = "2026101022001438170509972525";

    private RechargeOrderMapper rechargeOrderMapper;
    private PayCallbackService payCallbackService;
    private AlipayPayServiceImpl service;

    /** 「支付宝」的私钥（PKCS#8 的 Base64）—— 测试里用它签报文（真实世界只有支付宝自己有） */
    private String alipayPrivateKey;
    /** 「假支付宝」的私钥 —— 用来签出密码学上合法、但钥匙不对的伪造签名 */
    private String forgedPrivateKey;

    @BeforeEach
    void setUp() throws GeneralSecurityException {
        rechargeOrderMapper = mock(RechargeOrderMapper.class);
        payCallbackService = mock(PayCallbackService.class);

        KeyPair alipayKeys = generateKeyPair();
        alipayPrivateKey = base64(alipayKeys, false);
        forgedPrivateKey = base64(generateKeyPair(), false);

        // ★ 真的 new 一个官方 SDK 客户端出来（pageExecute 只在本地拼表单 + 签名，不发网络请求）：
        //   生产里它由 config/AlipaySdkConfig 提供，测试里用自己的密钥对就够了。
        //   注意构造器参数顺序：(网关地址, APPID, 应用私钥, format, charset, 支付宝公钥, 签名算法)
        AlipayClient alipayClient = new DefaultAlipayClient(
                "https://openapi-sandbox.dl.alipaydev.com/gateway.do",
                APP_ID, base64(alipayKeys, false), "json", "UTF-8", base64(alipayKeys, true), "RSA2");
        service = new AlipayPayServiceImpl(rechargeOrderMapper, payCallbackService, new ObjectMapper(), alipayClient);

        // @Value 字段是字段注入，单测里用反射塞进去（PKCS#8 / X.509 的 Base64，和支付宝密钥工具给的格式一致）
        ReflectionTestUtils.setField(service, "appId", APP_ID);
        ReflectionTestUtils.setField(service, "gatewayUrl", "https://openapi-sandbox.dl.alipaydev.com/gateway.do");
        ReflectionTestUtils.setField(service, "alipayPublicKey", base64(alipayKeys, true));
        ReflectionTestUtils.setField(service, "notifyUrl", "http://127.0.0.1:18082/api/pay/alipay/notify");
        ReflectionTestUtils.setField(service, "returnUrl", "http://127.0.0.1:18088/pay.html");
    }

    @Test
    @DisplayName("入账抛 BizException 时回 failure（回归：改之前异常会漏到全局处理器，被包成 Result JSON）")
    void shouldReturnFailureWhenCallbackThrows() throws Exception {
        // 库里的单：100.00 元，和报文对得上，才能走到入账那一步
        RechargeOrder order = new RechargeOrder();
        order.setAmount(10000L);
        when(rechargeOrderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);
        Mockito.doThrow(new BizException("充值单不存在：" + ORDER_NO))
                .when(payCallbackService).handlePayCallback(ORDER_NO, "SUCCESS", TRADE_NO, "");

        Map<String, String> params = notifyParams("TRADE_SUCCESS", "100.00");
        signAsAlipay(params, alipayPrivateKey);

        // ★ 这是本次修复的断言点：异常必须被 catch 住、显式回 "failure"，
        //   而不是漏出去让 @RestControllerAdvice 包成 {"success":false,...} 的 JSON
        assertEquals("failure", service.handleNotify(params));
    }

    @Test
    @DisplayName("正常入账回 success，并把渠道流水号原样传给记账方法")
    void shouldReturnSuccessWhenCredited() throws Exception {
        RechargeOrder order = new RechargeOrder();
        order.setAmount(10000L);
        when(rechargeOrderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);

        Map<String, String> params = notifyParams("TRADE_SUCCESS", "100.00");
        signAsAlipay(params, alipayPrivateKey);

        assertEquals("success", service.handleNotify(params));
        verify(payCallbackService).handlePayCallback(ORDER_NO, "SUCCESS", TRADE_NO, "");
    }

    @Test
    @DisplayName("伪造的签名（密码学合法但钥匙不对）验不过，回 failure 且一分钱不碰")
    void shouldReturnFailureWhenSignForged() throws Exception {
        Map<String, String> params = notifyParams("TRADE_SUCCESS", "100.00");
        signAsAlipay(params, forgedPrivateKey);

        assertEquals("failure", service.handleNotify(params));
        verify(payCallbackService, never()).handlePayCallback(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("报文金额和库里的对不上，回 failure 且不入账")
    void shouldReturnFailureWhenAmountMismatch() throws Exception {
        RechargeOrder order = new RechargeOrder();
        order.setAmount(10000L);
        when(rechargeOrderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);

        Map<String, String> params = notifyParams("TRADE_SUCCESS", "99.00");
        signAsAlipay(params, alipayPrivateKey);

        assertEquals("failure", service.handleNotify(params));
        verify(payCallbackService, never()).handlePayCallback(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("非成功状态（如 WAIT_BUYER_PAY）收下并回 success，让支付宝别再发，且不动账")
    void shouldReturnSuccessAndIgnoreWhenNotTradeSuccess() throws Exception {
        Map<String, String> params = notifyParams("WAIT_BUYER_PAY", "100.00");
        signAsAlipay(params, alipayPrivateKey);

        assertEquals("success", service.handleNotify(params));
        verify(payCallbackService, never()).handlePayCallback(anyString(), anyString(), anyString(), anyString());
    }

    /** 一份「支付宝通知」报文：还没签名（sign 由 signAsAlipay 补） */
    private Map<String, String> notifyParams(String tradeStatus, String totalAmount) {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", APP_ID);
        params.put("out_trade_no", ORDER_NO);
        params.put("trade_no", TRADE_NO);
        params.put("trade_status", tradeStatus);
        params.put("total_amount", totalAmount);
        // ★ 故意不放 sign_type：SDK 的 rsaSign 会把它一起签进去，而 rsaCheckV1 验签前会先删掉它 ——
        //   放了就必然验不过。（真实报文里是有的，因为支付宝服务端按自己的规则签，那条规则不归我们管。）
        return params;
    }

    /** 扮演支付宝签名：直接用官方 SDK 的 rsaSign（和验签同一套规则，天然对称） */
    private void signAsAlipay(Map<String, String> params, String privateKey) throws AlipayApiException {
        // 4 参重载要的是【拼好的待签串】+ 明确的算法（"RSA2" = SHA256withRSA）；
        // 3 参的 Map 重载走的是默认算法（RSA = SHA1withRSA），和验签那边的 RSA2 对不上。
        String content = AlipaySignature.getSignContent(params);
        params.put("sign", AlipaySignature.rsaSign(content, privateKey, "UTF-8", "RSA2"));
    }

    private KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /** 私钥是 PKCS#8、公钥是 X.509 的 Base64 —— 支付宝密钥工具给的就是这两个格式 */
    private String base64(KeyPair keyPair, boolean publicKey) {
        byte[] encoded = publicKey ? keyPair.getPublic().getEncoded() : keyPair.getPrivate().getEncoded();
        return Base64.getEncoder().encodeToString(encoded);
    }
}
