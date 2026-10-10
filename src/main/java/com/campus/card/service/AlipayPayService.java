package com.campus.card.service;

import com.campus.card.vo.AlipayPayVO;
import com.campus.card.vo.LoginUser;

import java.util.Map;

/**
 * @Description 支付宝：发起支付 + 处理异步通知
 * @Author u
 * @Date 2026/10/10
 */
public interface AlipayPayService {

    /**
     * 学生点「去支付宝付款」：确认这张单是他的、状态能付，然后返回签好名的参数
     *
     * @param orderNo 平台充值单号
     * @param user    当前登录的学生
     * @return {@link AlipayPayVO }
     */
    AlipayPayVO prepay(String orderNo, LoginUser user);

    /**
     * 支付宝打过来的通知：验签 → 核对 → 交给充值回调记账
     *
     * @param params 支付宝 POST 过来的整张参数表
     * @return 只能是 success 或 failure —— 返回什么字由支付宝定，不由我们的 Result 规范定
     */
    String handleNotify(Map<String, String> params);
}