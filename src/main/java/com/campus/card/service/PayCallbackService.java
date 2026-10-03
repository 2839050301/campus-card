package com.campus.card.service;

import com.campus.card.vo.PayCallbackVO;

/**
 * @Description
 * @Author u
 * @Date 2026/10/3
 */
public interface PayCallbackService {

    /**
     * 处理支付回调
     *
     * @param orderNo 平台充值订单
     * @param result  SUCCESS / 其它（其它一律当失败）果
     * @return {@link PayCallbackVO }
     */
    PayCallbackVO handlePayCallback(String orderNo, String result);
}
