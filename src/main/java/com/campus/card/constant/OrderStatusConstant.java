package com.campus.card.constant;

/**
 * @Description
 * @Author u
 * @Date 2026/10/3
 */
public class OrderStatusConstant {
    private OrderStatusConstant(){}


    /**
     * 等待付款
     */
    public static final int WAITING_FOR_PAYMENT=0;

    /**
     * 支付中(渠道已受理，结果未知)
     */
    public static final int PAYING=1;

    /**
     * 支付成功(已入账)
     */
    public static final int PAID=2;


    /**
     * 支付失败
     */
    public static final int FAILED=3;

    /**
     * 已关闭（超市未付款/超限额被拒/人工关闭）
     */
    public static final int CLOSED=4;


}
