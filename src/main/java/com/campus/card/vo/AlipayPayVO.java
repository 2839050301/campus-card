package com.campus.card.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * @Description 发起支付宝支付时交给前端的 action + biz_content（另附方便排查的原始参数表）
 * @Author u
 * @Date 2026/10/10
 */
@Data
@AllArgsConstructor
public class AlipayPayVO {

    /**
     * 沙盒网关地址
     */
    private String gatewayUrl;
    /**
     * 已经签好名的参数表（含 sign）—— 排查报文用，前端真正要的是下面的 action
     */
    private Map<String, String> params;
    /**
     * ★ 表单往哪儿提交：网关地址 + ? + 除 biz_content 外所有参数（已 URL 编码）
     */
    private String action;
    /**
     * ★ 表单里唯一那个字段：POST 体只送它
     */
    private String bizContent;
}
