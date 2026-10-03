package com.campus.card.constant;

/**
 * @Description
 * @Author u
 * @Date 2026/10/2
 */
public class LimitConstant {

    private LimitConstant() {

    }

    /** 单笔限额：1000 元（100000 分） */
    public static final long SINGLE_LIMIT = 100_000L;

    /** 单日累计限额：2000 元（200000 分） */
    public static final long DAILY_LIMIT = 200_000L;

    /** 单笔最低：1 元 */
    public static final long MIN_AMOUNT = 100L;

    /** 充值金额必须是 1 元的整数倍（100 分） */
    public static final long AMOUNT_STEP = 100L;
}
