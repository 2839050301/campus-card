package com.campus.card.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.card.vo.RechargeOrderVO;
import com.campus.card.vo.TodayStatVO;

/**
 * @Description
 * @Author u
 * @Date 2026/10/8
 */
public interface AdminRechargeService {
    /**
     * 首页 6 张卡
     */
    TodayStatVO today();

    /**
     * 充值单分页
     *
     * @param current   页码，从 1 开始
     * @param size      每页条数
     * @param status    订单状态，null 表示不筛
     * @param payMethod 支付方式，null/空 表示不筛
     * @param keyword   关键字，同时匹配单号、学号、姓名
     */
    IPage<RechargeOrderVO> page(long current, long size, Integer status,
                                String payMethod, String keyword);


    /**
     * 把某单的通知放回待投递队列，由调度器重新投递
     */
    RechargeOrderVO retryNotify(String orderNo);

    /**
     * 人工补单：把一笔没付成功的单子改成支付成功，走和渠道回调相同的入账链路
     *
     * @param operator 操作人账号，会写进订单备注留痕
     */
    RechargeOrderVO mockPaid(String orderNo, String operator);
}