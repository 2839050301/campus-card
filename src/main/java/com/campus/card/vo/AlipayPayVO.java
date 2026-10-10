package com.campus.card.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Description 发起支付宝支付时交给前端的东西：官方 SDK 生成好的那一整段表单 HTML
 * @Author u
 * @Date 2026/10/10
 */
@Data
@AllArgsConstructor
public class AlipayPayVO {

    /**
     * 沙盒网关地址（只用来打日志 / 排查；真正要跳的地址在 formHtml 的 action 里）
     */
    private String gatewayUrl;

    /**
     * ★ 前端唯一要用的东西：官方 SDK 生成好的整段表单 HTML。
     *   它的 action 里已经带好全部签名参数（sign / sign_type / charset / method / timestamp / app_id …），
     *   体内只有一个隐藏字段 biz_content —— 「除 biz_content 外都放查询串、biz_content 放 POST 体」
     *   这个形状现在由 SDK 保证，不再由我们手拼。
     *   ★ 为什么不是「返回 action + biz_content，让前端自己拼 form」：
     *     官方 SDK 对页面跳转类接口【不】把参数表给你 —— pageExecute 之后 getParams() 是 null，
     *     它只给整段 HTML（实测，见 README「Day 11 增补」）。
     *     想要参数就得去解析它生成的 HTML，那是拿正则解别人的产物；
     *     不如直接把它的产物交给浏览器 —— 官方 demo 也是这么用的。
     */
    private String formHtml;
}
