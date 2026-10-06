package com.campus.card.constant;

/**
 * 账户流水类型
 *
 * @author 87
 * @date 2026/10/05
 */
public class FlowTypeConstant {
    private FlowTypeConstant() {}

    public static final String RECHARGE = "RECHARGE";   // 充值入账
    public static final String CONSUME  = "CONSUME";    // 消费扣款
    public static final String REFUND   = "REFUND";     // 退款出账
    public static final String ADJUST   = "ADJUST";     // 人工调账

}
