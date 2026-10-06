package com.campus.card.constant;

/**
 * @Description 对账相关常量
 * @Author u
 * @Date 2026/10/5
 */
public class ReconConstant {
    private ReconConstant() {}


    /* ---------- t_recon_task.status ---------- */
    public static final int TASK_RUNNING = 0;   // 进行中
    public static final int TASK_DONE    = 1;   // 已完成
    public static final int TASK_FAIL    = 2;   // 失败

    /* ---------- t_recon_diff.diff_type ---------- */
    /** 短款：平台有、渠道没有 —— 钱比账少，得去追（四类里唯一会真丢钱的） */
    public static final String DIFF_LOCAL_ONLY   = "LOCAL_ONLY";
    /** 长款：渠道有、平台没有 —— 钱比账多，补单即可；但自己系统里查不到，只有对账能发现 */
    public static final String DIFF_CHANNEL_ONLY = "CHANNEL_ONLY";
    /** 两边都有但金额不一样 */
    public static final String DIFF_AMOUNT_DIFF  = "AMOUNT_DIFF";
    /** 两边都有但状态不一样 */
    public static final String DIFF_STATUS_DIFF  = "STATUS_DIFF";
    /** 已收款、有单、但账户流水缺失 —— 只有对三方账才能发现 */
    public static final String DIFF_NOT_POSTED   = "NOT_POSTED";

    /* ---------- t_recon_diff.handled ---------- */
    public static final int UNHANDLED = 0;
    public static final int HANDLED   = 1;

    /* ---------- 渠道 ---------- */
    public static final String DEFAULT_CHANNEL = "WECHAT";
    /** 渠道账单里「这笔交易成功」的标记 */
    public static final String TRADE_SUCCESS = "SUCCESS";

    /* ---------- ★ 只为演示造数用的三个参数 ---------- */
    /** 造一笔「渠道有、平台没有」的长款，金额 50 元 */
    public static final long DEMO_GHOST_AMOUNT = 5_000L;
    /** 把渠道账单上某一笔金额改大 1 元，造金额不符 */
    public static final long DEMO_AMOUNT_DIFF  = 100L;
}
