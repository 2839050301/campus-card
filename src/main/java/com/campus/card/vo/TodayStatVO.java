package com.campus.card.vo;

import lombok.Data;

/**
 * @Description 管理端首页 6 张卡 —— GET /api/admin/stat/today
 * @Author u
 * @Date 2026/10/8
 */
@Data
public class TodayStatVO {

    private Long orderCount;        // 今日充值单总数
    private Long paidCount;         // 今日支付成功笔数
    private Long paidAmount;        // 今日支付成功金额合计（分）
    private Double successRate;     // 支付成功率，百分比数值：70 表示 70.0%
    private Long notifyFailCount;   // 通知终端最终失败的条数（不按日期过滤）
    private Long balanceTotal;      // 全部账户余额合计（分）

}
